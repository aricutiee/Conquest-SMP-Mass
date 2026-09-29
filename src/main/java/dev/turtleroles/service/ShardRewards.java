package dev.turtleroles.service;
import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import java.nio.file.Path;
import java.util.*;

public final class ShardRewards implements Listener,CommandExecutor,AutoCloseable {
 private final TurtleRolesPlugin plugin;private final Path file;private YamlConfiguration data;
 private final Set<UUID> selecting=new HashSet<>();private final Map<UUID,Location> first=new HashMap<>();
 private final Map<UUID,Long> waiting=new HashMap<>();private BukkitTask task;private long ticks;
 public ShardRewards(TurtleRolesPlugin p){plugin=p;file=p.getDataFolder().toPath().resolve("afk-zone.yml");data=YamlConfiguration.loadConfiguration(file.toFile());}
 public void start(){Bukkit.getPluginManager().registerEvents(this,plugin);plugin.getCommand("afk").setExecutor(this);plugin.getCommand("shards").setExecutor(this);task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);}
 static boolean admin(TurtleRolesPlugin p,CommandSender s){return !(s instanceof Player player)||player.isOp()||s.hasPermission("conquest.npc.admin")||p.roleService().roleOf(player.getUniqueId()).weight()>=Role.ADMIN.weight();}
 public void cancelSelection(UUID id){selecting.remove(id);first.remove(id);}
 public boolean inside(Location l){return data.getBoolean("enabled")&&l.getWorld()!=null&&l.getWorld().getUID().toString().equals(data.getString("world"))&&l.getX()>=data.getDouble("min-x")&&l.getX()<data.getDouble("max-x")+1&&l.getY()>=data.getDouble("min-y")&&l.getY()<data.getDouble("max-y")+1&&l.getZ()>=data.getDouble("min-z")&&l.getZ()<data.getDouble("max-z")+1;}
 void tick(){ticks+=20;Map<UUID,Long> awards=new HashMap<>();Set<UUID> eligible=new HashSet<>();
  for(Player p:Bukkit.getOnlinePlayers())if(!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&ClientCompatibility.authenticated(p)&&inside(p.getLocation())){UUID id=p.getUniqueId();eligible.add(id);long next=waiting.computeIfAbsent(id,k->ticks+40);if(ticks>=next){awards.put(id,1L);waiting.put(id,ticks+60);}}
  waiting.keySet().retainAll(eligible);if(!awards.isEmpty())try{plugin.races().store().creditBatch(awards);}catch(Exception e){plugin.getLogger().warning("Could not save AFK shard awards: "+e.getMessage());}
 }
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent e){if(e.getTo()!=null){if(!inside(e.getTo()))waiting.remove(e.getPlayer().getUniqueId());else if(!inside(e.getFrom()))waiting.put(e.getPlayer().getUniqueId(),ticks+60);}}
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){waiting.remove(e.getPlayer().getUniqueId());}
 @EventHandler public void quit(PlayerQuitEvent e){waiting.remove(e.getPlayer().getUniqueId());selecting.remove(e.getPlayer().getUniqueId());first.remove(e.getPlayer().getUniqueId());}
 @EventHandler(priority=EventPriority.MONITOR) public void death(PlayerDeathEvent e){Player p=e.getEntity().getKiller();waiting.remove(e.getEntity().getUniqueId());if(p==null||p==e.getEntity())return;try{if(plugin.races().store().rewardKill(p.getUniqueId(),e.getEntity().getUniqueId(),System.currentTimeMillis()))p.sendMessage("+25 shards for the kill.");else p.sendMessage("No shards: this victim's three-kill allowance is exhausted until its 72-hour window resets.");}catch(Exception ex){plugin.getLogger().warning("Could not save kill shards: "+ex.getMessage());}}
 @EventHandler(priority=EventPriority.HIGHEST) public void select(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND||!selecting.contains(e.getPlayer().getUniqueId())||e.getClickedBlock()==null)return;e.setCancelled(true);Player p=e.getPlayer();if(!admin(plugin,p)){selecting.remove(p.getUniqueId());return;}
  if(e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_BLOCK){first.put(p.getUniqueId(),e.getClickedBlock().getLocation());p.sendMessage("First AFK corner selected. Right-click the opposite corner, including its height.");}
  else if(e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK){Location a=first.get(p.getUniqueId()),b=e.getClickedBlock().getLocation();if(a==null||a.getWorld()!=b.getWorld()){p.sendMessage("Select the first corner in this world first.");return;}
   YamlConfiguration next=new YamlConfiguration();next.set("enabled",true);next.set("world",a.getWorld().getUID().toString());next.set("min-x",Math.min(a.getBlockX(),b.getBlockX()));next.set("max-x",Math.max(a.getBlockX(),b.getBlockX()));next.set("min-y",Math.min(a.getBlockY(),b.getBlockY()));next.set("max-y",Math.max(a.getBlockY(),b.getBlockY()));next.set("min-z",Math.min(a.getBlockZ(),b.getBlockZ()));next.set("max-z",Math.max(a.getBlockZ(),b.getBlockZ()));
   try{AtomicYaml.save(next,file);data=next;waiting.clear();selecting.remove(p.getUniqueId());first.remove(p.getUniqueId());p.sendMessage("AFK zone saved, including both selected heights. One shard every three seconds inside.");}catch(Exception ex){p.sendMessage("Could not save the AFK zone.");}
  }
 }
 @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a){
  if(c.getName().equals("shards")){if(a.length==0){if(s instanceof Player p)p.sendMessage("Shards: "+plugin.races().state(p).shards);return true;}if(!admin(plugin,s)){s.sendMessage("Only administrators can grant shards.");return true;}
   if(a.length==3&&a[0].equalsIgnoreCase("give")){Player online=Bukkit.getPlayerExact(a[1]);UUID id=online==null?plugin.races().store().lookup(a[1]):online.getUniqueId();try{long amount=Long.parseLong(a[2]);if(id==null||amount<1||!plugin.races().store().addShards(id,amount)){s.sendMessage("Use a known player and a positive shard amount.");return true;}s.sendMessage("Granted "+amount+" shards to "+a[1]+".");}catch(Exception ex){s.sendMessage("Could not grant shards. Check the amount and server storage.");}return true;}s.sendMessage("/shards or /shards give <player> <amount>");return true;
  }
  if(!admin(plugin,s)){s.sendMessage("Only administrators can select the AFK zone.");return true;}
  if(a.length==1&&a[0].equalsIgnoreCase("off")){boolean old=data.getBoolean("enabled");data.set("enabled",false);try{AtomicYaml.save(data,file);waiting.clear();s.sendMessage("AFK rewards disabled.");}catch(Exception ex){data.set("enabled",old);s.sendMessage("Could not save.");}return true;}
  if(!(s instanceof Player p)){s.sendMessage("Select the AFK area in-game.");return true;}if(a.length==1&&a[0].equalsIgnoreCase("cancel")){selecting.remove(p.getUniqueId());first.remove(p.getUniqueId());p.sendMessage("AFK selection cancelled.");return true;}
  plugin.survival().spawnArea().cancelSelection(p.getUniqueId());selecting.add(p.getUniqueId());first.remove(p.getUniqueId());p.sendMessage("Left-click one AFK corner, then right-click the opposite corner. Height is bounded by these two blocks. /afk cancel cancels.");return true;
 }
 @Override public void close(){if(task!=null)task.cancel();HandlerList.unregisterAll(this);waiting.clear();selecting.clear();first.clear();}
}

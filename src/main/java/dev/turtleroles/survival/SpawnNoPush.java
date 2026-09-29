package dev.turtleroles.survival;

import dev.turtleroles.TurtleRolesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import java.util.*;

/** Spawn-only collision leases, preserving existing team formatting and player state. */
public final class SpawnNoPush implements Listener,AutoCloseable {
 private final TurtleRolesPlugin plugin;private final SpawnArea area;
 private final Map<UUID,Boolean> prior=new HashMap<>();
 private record Lease(Team team,Team original){}
 private final Map<Scoreboard,Map<String,Lease>> boards=new IdentityHashMap<>();private BukkitTask task;
 public SpawnNoPush(TurtleRolesPlugin plugin,SpawnArea area){this.plugin=plugin;this.area=area;}
 public void start(){Bukkit.getPluginManager().registerEvents(this,plugin);task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,5);}
 private void state(Player p,boolean inside){
  if(inside){prior.putIfAbsent(p.getUniqueId(),p.isCollidable());p.setCollidable(false);}
  else {Boolean old=prior.remove(p.getUniqueId());if(old!=null&&!p.isCollidable())p.setCollidable(old);}
 }
 void tick(){
  Map<String,Player> safe=new HashMap<>();Set<Scoreboard> active=Collections.newSetFromMap(new IdentityHashMap<>());
  for(Player p:Bukkit.getOnlinePlayers()){boolean inside=area.inside(p.getLocation());state(p,inside);if(inside)safe.put(p.getName(),p);active.add(p.getScoreboard());}
  for(Scoreboard board:active){
   Map<String,Lease> leases=boards.computeIfAbsent(board,k->new HashMap<>());
   // Event/sidebar boards may copy our temporary teams. Adopt those copies so
   // their collision rule is also restored when the player leaves spawn.
   for(Team team:new ArrayList<>(board.getTeams()))if(team.getName().startsWith("cq_np_")&&!leases.values().stream().anyMatch(l->l.team.equals(team))){
    if(team.getEntries().isEmpty()){team.unregister();continue;}
    for(String name:team.getEntries())leases.put(name,new Lease(team,inherited(board,name)));
   }
   leases.entrySet().removeIf(e->{if(!safe.containsKey(e.getKey())||!Objects.equals(board.getEntryTeam(e.getKey()),e.getValue().team)){release(board,e.getKey(),e.getValue());return true;}return false;});
   for(var entry:safe.entrySet()){
    String name=entry.getKey();Lease lease=leases.get(name);
    if(lease==null){String id="cq_np_"+entry.getValue().getUniqueId().toString().replace("-","").substring(0,9);if(board.getTeam(id)!=null)continue;
     Team original=board.getEntryTeam(name);if(original==null)original=inherited(board,name);Team team=board.registerNewTeam(id);lease=new Lease(team,original);leases.put(name,lease);if(original!=null)original.removeEntry(name);team.addEntry(name);
    }
    if(lease.original!=null&&lease.original.getScoreboard()!=null){Team a=lease.original,b=lease.team;if(!b.prefix().equals(a.prefix()))b.prefix(a.prefix());if(!b.suffix().equals(a.suffix()))b.suffix(a.suffix());if(b.getColor()!=a.getColor())b.setColor(a.getColor());if(b.allowFriendlyFire()!=a.allowFriendlyFire())b.setAllowFriendlyFire(a.allowFriendlyFire());if(b.canSeeFriendlyInvisibles()!=a.canSeeFriendlyInvisibles())b.setCanSeeFriendlyInvisibles(a.canSeeFriendlyInvisibles());for(Team.Option o:Team.Option.values())if(o!=Team.Option.COLLISION_RULE&&a.getOption(o)!=null&&a.getOption(o)!=b.getOption(o))b.setOption(o,a.getOption(o));}
    if(lease.team.getOption(Team.Option.COLLISION_RULE)!=Team.OptionStatus.NEVER)lease.team.setOption(Team.Option.COLLISION_RULE,Team.OptionStatus.NEVER);
   }
  }
  boards.entrySet().removeIf(e->{if(active.contains(e.getKey()))return false;e.getValue().forEach((name,lease)->release(e.getKey(),name,lease));return true;});
 }
 private Team inherited(Scoreboard board,String name){for(var map:boards.values()){Lease old=map.get(name);if(old!=null&&old.original!=null&&old.original.getScoreboard()!=null){Team original=board.getTeam(old.original.getName());if(original!=null)return original;}}return null;}
 private static void release(Scoreboard board,String name,Lease lease){
  if(lease.team.getScoreboard()==null)return;boolean ours=Objects.equals(board.getEntryTeam(name),lease.team);
  lease.team.unregister();if(ours&&lease.original!=null&&lease.original.getScoreboard()!=null)lease.original.addEntry(name);
 }
 @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent e){if(e.getTo()!=null)state(e.getPlayer(),area.inside(e.getTo()));}
 @EventHandler public void quit(PlayerQuitEvent e){state(e.getPlayer(),false);}
 @Override public void close(){if(task!=null)task.cancel();for(Player p:Bukkit.getOnlinePlayers())state(p,false);boards.forEach((board,leases)->leases.forEach((name,lease)->release(board,name,lease)));boards.clear();HandlerList.unregisterAll(this);}
}

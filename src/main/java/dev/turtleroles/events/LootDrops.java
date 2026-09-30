package dev.turtleroles.events;

import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Bounded asynchronous surface searches. Only the main thread places or fills chests. */
public final class LootDrops implements Listener,AutoCloseable {
    private final TurtleRolesPlugin plugin;
    private final YamlConfiguration config,state;
    private final Path file;
    private final LootDropSchedule schedule;
    private final Random random=new Random();
    private final int minimum,maximum,attempts;
    private final Sound sound;
    private BukkitTask ticker;
    private boolean searching,closed;
    private long searchStarted;
    private int generation;
    private static final class Menu implements InventoryHolder {
        Inventory inventory;
        public Inventory getInventory(){return inventory;}
    }
    public LootDrops(TurtleRolesPlugin plugin) {
        this.plugin=plugin;
        File settings=new File(plugin.getDataFolder(),"loot-drops.yml");
        if(!settings.exists())plugin.saveResource("loot-drops.yml",false);
        config=YamlConfiguration.loadConfiguration(settings);
        file=plugin.getDataFolder().toPath().resolve("loot-drop-state.yml");
        state=YamlConfiguration.loadConfiguration(file.toFile());
        schedule=new LootDropSchedule(System.currentTimeMillis(),state.getLong("next-drop"),
                state.getBoolean("automatic",config.getBoolean("automatic",true)),
                Math.clamp(config.getLong("interval-seconds",14400),60,31_536_000)*1000);
        minimum=Math.clamp(config.getInt("minimum-radius",500),1,100_000);
        maximum=Math.clamp(config.getInt("maximum-radius",1500),minimum+1,100_001);
        attempts=Math.clamp(config.getInt("maximum-location-attempts",40),1,100);
        Sound selected;
        try {selected=Sound.valueOf(config.getString("sound","ENTITY_ENDER_DRAGON_GROWL"));}
        catch(IllegalArgumentException ex){selected=Sound.ENTITY_ENDER_DRAGON_GROWL;}
        sound=selected;
    }
    public void start() {
        Bukkit.getPluginManager().registerEvents(this,plugin);
        if(!save())schedule.automatic=false;
        ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,200,200);
        plugin.getLogger().info("Loot drops ready: "+status());
    }
    private void tick() {
        long now=System.currentTimeMillis();
        if(searching && now-searchStarted>120_000) {
            generation++;fail(null,"Surface search timed out; will retry in one minute.");
        }
        if(!searching && schedule.due(now) && !Bukkit.getOnlinePlayers().isEmpty())spawn(null);
    }
    private boolean admin(CommandSender sender) {
        return sender.hasPermission("shocksmp.events.admin") || sender instanceof Player player
                && plugin.roleService().effectiveRoleOf(player.getUniqueId()).weight()>=Role.ADMIN.weight();
    }
    public void command(CommandSender sender,String[] args) {
        if(!admin(sender)){sender.sendMessage("Loot drops require event administrator permission.");return;}
        String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        switch(action) {
            case "menu" -> {if(sender instanceof Player player)open(player);else sender.sendMessage(status());}
            case "start","spawn" -> spawn(sender);
            case "status" -> sender.sendMessage(status());
            case "auto" -> {
                if(args.length!=2 || !(args[1].equalsIgnoreCase("on")||args[1].equalsIgnoreCase("off")))
                    sender.sendMessage("Usage: /events loot auto <on|off>");
                else {setAutomatic(args[1].equalsIgnoreCase("on"));sender.sendMessage(status());}
            }
            default -> sender.sendMessage("/events loot [start|status|auto on|auto off]");
        }
    }
    private String status() {
        return "Automatic: "+schedule.automatic+" | next in "+Math.max(0,(schedule.next-System.currentTimeMillis()+999)/1000)
                +"s"+(searching?" | finding safe ground":"")+" | last drop: "+state.getString("last-drop","none");
    }
    private void setAutomatic(boolean value) {
        boolean old=schedule.automatic;long deadline=schedule.next;
        schedule.automatic(value,System.currentTimeMillis());
        if(!save()){schedule.automatic=old;schedule.next=deadline;}
    }
    private void spawn(CommandSender sender) {
        if(closed)return;
        if(searching){if(sender!=null)sender.sendMessage("A loot drop is already being prepared.");return;}
        Location center=plugin.survival().spawn().clone();
        if(center.getWorld()==null || center.getWorld().getEnvironment()!=World.Environment.NORMAL) {
            if(sender!=null)sender.sendMessage("No overworld spawn is available.");return;
        }
        long previous=schedule.next;
        schedule.reserve(System.currentTimeMillis());
        // Reserve before generating any chunks or loot. A restart cannot duplicate a successful drop.
        if(!save()){schedule.next=previous;if(sender!=null)sender.sendMessage("Could not save the drop timer; no chest created.");return;}
        searching=true;searchStarted=System.currentTimeMillis();int token=++generation;
        if(sender!=null)sender.sendMessage("Finding safe ground "+minimum+"-"+maximum+" blocks from world spawn...");
        find(center,attempts,token,sender);
    }
    private void find(Location center,int left,int token,CommandSender sender) {
        if(closed || token!=generation)return;
        if(left<=0){fail(sender,"Could not find safe ground inside the border; retrying in one minute.");return;}
        World world=center.getWorld();
        LootDropRules.Offset offset=LootDropRules.offset(random,minimum,maximum);
        int x=center.getBlockX()+offset.x(),z=center.getBlockZ()+offset.z();
        // Keep neighbor checks inside the same asynchronously loaded chunk.
        if((x&15)==0||(x&15)==15||(z&15)==0||(z&15)==15
                || !world.getWorldBorder().isInside(new Location(world,x+.5,world.getMinHeight()+1,z+.5))) {
            Bukkit.getScheduler().runTask(plugin,()->find(center,left-1,token,sender));return;
        }
        world.getChunkAtAsync(x>>4,z>>4,true).whenComplete((chunk,error)->{
            if(!plugin.isEnabled())return;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(closed || token!=generation)return;
                if(error!=null){find(center,left-1,token,sender);return;}
                Location latest=plugin.survival().spawn();
                if(!world.equals(latest.getWorld()) || latest.distanceSquared(center)>1) {
                    fail(sender,"World spawn changed during preparation; retrying in one minute.");return;
                }
                int y=world.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES)+1;
                if(y>=world.getMaxHeight()-1){find(center,left-1,token,sender);return;}
                Block place=world.getBlockAt(x,y,z);
                if(!safe(place) || !LootDropRules.inRange(x+.5-center.getX(),z+.5-center.getZ(),minimum,maximum)) {
                    find(center,left-1,token,sender);return;
                }
                place(place,sender);
            });
        });
    }
    static boolean safe(Block place) {
        if(!place.getType().isAir() || !place.getRelative(BlockFace.UP).getType().isAir())return false;
        Material floor=place.getRelative(BlockFace.DOWN).getType();
        if(!floor.isSolid() || Tag.LEAVES.isTagged(floor) || floor==Material.MAGMA_BLOCK
                || floor==Material.CACTUS || floor==Material.CAMPFIRE || floor==Material.SOUL_CAMPFIRE)return false;
        for(BlockFace face:List.of(BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST)) {
            Material beside=place.getRelative(face).getType();
            if(beside==Material.CHEST||beside==Material.TRAPPED_CHEST||beside==Material.LAVA||beside==Material.FIRE)return false;
        }
        return true;
    }
    private void place(Block block,CommandSender sender) {
        Material original=block.getType();
        boolean populated=false;
        try {
            ItemStack[] contents=LootDropRules.scatter(LootDropRules.loot(config,random),random);
            block.setType(Material.CHEST,false);
            Chest chest=(Chest)block.getState();
            chest.customName(Component.text("Loot Drop",NamedTextColor.LIGHT_PURPLE));
            if(!chest.update(false,false))throw new IllegalStateException("Chest changed before placement completed");
            chest.getBlockInventory().setContents(contents);
            populated=true;
            searching=false;
            String coordinates=block.getWorld().getName()+" | X: "+block.getX()+" Y: "+block.getY()+" Z: "+block.getZ();
            state.set("last-drop",coordinates);save();
            String title=config.getString("title","LOOT DROP HAS SPAWNED");
            for(Player observer:Bukkit.getOnlinePlayers())dev.turtleroles.analytics.ConquestAnalytics.action(observer,"MARK_PRESENT","supply_drop_spawn");
            Bukkit.broadcast(Component.text(title+"! "+coordinates,NamedTextColor.GOLD));
            for(Player player:Bukkit.getOnlinePlayers()) {
                player.showTitle(Title.title(Component.text(title,NamedTextColor.RED),Component.text(coordinates,NamedTextColor.GOLD)));
                player.playSound(player.getLocation(),sound,(float)Math.clamp(config.getDouble("sound-volume",1),0,2),
                        (float)Math.clamp(config.getDouble("sound-pitch",1),.5,2));
            }
            plugin.getLogger().info("Loot drop created: "+coordinates);
            if(sender!=null)sender.sendMessage("Loot drop created: "+coordinates);
        } catch(RuntimeException ex) {
            if(populated) {
                searching=false;
                plugin.getLogger().log(java.util.logging.Level.SEVERE,"Loot chest exists but its announcement failed",ex);
                if(sender!=null)sender.sendMessage("Chest created at "+block.getX()+", "+block.getY()+", "+block.getZ()+"; announcement failed.");
                return;
            }
            if(block.getType()==Material.CHEST)block.setType(original,false);
            fail(sender,"Could not create loot chest: "+ex.getMessage());
        }
    }
    private void fail(CommandSender sender,String message) {
        searching=false;schedule.next=System.currentTimeMillis()+60_000;
        if(!save())schedule.automatic=false;
        plugin.getLogger().warning(message);if(sender!=null)sender.sendMessage(message);
    }
    private boolean save() {
        state.set("automatic",schedule.automatic);state.set("next-drop",schedule.next);
        Path temporary=file.resolveSibling(file.getFileName()+".tmp");
        try {
            Files.createDirectories(file.getParent());Files.writeString(temporary,state.saveToString());
            try {Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(IOException ex){plugin.getLogger().severe("Could not save loot-drop timer: "+ex.getMessage());return false;}
    }
    public void open(Player player) {
        if(!admin(player)){player.sendMessage("Loot drops require event administrator permission.");return;}
        Menu holder=new Menu();holder.inventory=Bukkit.createInventory(holder,27,Component.text("Conquest Loot Drops"));
        holder.inventory.setItem(11,button(Material.CHEST,"Spawn Loot Drop Now","Find safe ground and announce the coordinates"));
        holder.inventory.setItem(15,button(schedule.automatic?Material.LIME_DYE:Material.GRAY_DYE,"Automatic: "+schedule.automatic,"Toggle the four-hour schedule"));
        holder.inventory.setItem(22,button(Material.CLOCK,"Drop Status",status()));
        player.openInventory(holder.inventory);
    }
    private ItemStack button(Material type,String name,String lore) {
        ItemStack item=new ItemStack(type);var meta=item.getItemMeta();
        meta.displayName(Component.text(name,NamedTextColor.LIGHT_PURPLE));meta.lore(List.of(Component.text(lore,NamedTextColor.GRAY)));
        item.setItemMeta(meta);return item;
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||!admin(player))return;
        if(event.getClick()!=ClickType.LEFT && event.getClick()!=ClickType.RIGHT)return;
        if(event.getRawSlot()==11){player.closeInventory();spawn(player);}
        if(event.getRawSlot()==15){setAutomatic(!schedule.automatic);open(player);}
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);
    }
    @Override public void close() {closed=true;generation++;if(ticker!=null)ticker.cancel();HandlerList.unregisterAll(this);save();}
}

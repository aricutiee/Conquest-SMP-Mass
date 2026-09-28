package dev.turtleroles.events;

import dev.turtleroles.TurtleRolesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.util.*;

/** Real public chests. Save placement intent before touching terrain, never recreate delivered loot. */
final class WarlordHunt implements Listener,AutoCloseable {
    private final JavaPlugin plugin;
    private final WarlordRelics relics;
    private final WarlordAnimation animation;
    private final File file;
    private final YamlConfiguration state,config;
    private final Random random=new Random();
    private boolean closed,searching;
    private final NamespacedKey chestKey=new NamespacedKey("conquestsmp","purge_chest");
    private static final class Menu implements InventoryHolder {Inventory inventory;public Inventory getInventory(){return inventory;}}
    WarlordHunt(JavaPlugin plugin,YamlConfiguration config,WarlordRelics relics) {
        this.plugin=plugin;this.config=config;this.relics=relics;animation=new WarlordAnimation(plugin);
        file=new File(plugin.getDataFolder(),"warlord-hunt.yml");state=YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getScheduler().runTaskLater(plugin,this::resume,60);
    }
    boolean outstanding(){return WarlordRelics.PIECES.stream().anyMatch(p->state.contains("pieces."+p)&&!state.getString("pieces."+p+".status","").equals("claimed"));}
    void begin(UUID event,Location location,Map<String,ItemStack> items) {
        if(event.toString().equals(state.getString("event")))return;
        if(outstanding())throw new IllegalStateException("Finish the previous Warlord hunt before another Purge.");
        state.set("event",event.toString());state.set("pieces",null);
        Location center=plugin instanceof TurtleRolesPlugin combined?combined.survival().spawn():location.getWorld().getSpawnLocation();
        state.set("center",center);
        for(String piece:WarlordRelics.PIECES){state.set("pieces."+piece+".item",items.getOrDefault(piece,relics.create(piece)));state.set("pieces."+piece+".status","pending");}
        save();
        animation.play(location,items.values().stream().toList(),false,()->{
            Bukkit.broadcast(Component.text("WARLORD'S PURGE: The seven relics have scattered. Their locations will be revealed by an administrator.",NamedTextColor.LIGHT_PURPLE));resume();
        });
    }
    void preview(Player player) {
        animation.play(player.getLocation(),WarlordRelics.PIECES.stream().map(relics::create).toList(),true,()->{});
        player.sendMessage("Animation preview only. No event ended and no rewards created.");
    }
    private void resume() {
        if(closed||searching)return;
        for(String piece:WarlordRelics.PIECES)if(state.getString("pieces."+piece+".status","").equals("pending")) {
            Location center=state.getLocation("center");if(center==null||center.getWorld()==null)return;
            searching=true;find(piece,center,60);return;
        }
    }
    private void find(String piece,Location center,int tries) {
        if(closed)return;
        if(tries<=0){searching=false;plugin.getLogger().warning("No safe site for Warlord "+piece+". Use /warlordevent retry.");return;}
        int min=Math.clamp(config.getInt("warlord-purge.minimum-radius",500),50,10000);
        int max=Math.clamp(config.getInt("warlord-purge.maximum-radius",1500),min+1,20000);
        var offset=LootDropRules.offset(random,min,max);int x=center.getBlockX()+offset.x(),z=center.getBlockZ()+offset.z();World world=center.getWorld();
        if((x&15)==0||(x&15)==15||(z&15)==0||(z&15)==15||!world.getWorldBorder().isInside(new Location(world,x+.5,world.getMinHeight()+1,z+.5))) {
            Bukkit.getScheduler().runTask(plugin,()->find(piece,center,tries-1));return;
        }
        world.getChunkAtAsync(x>>4,z>>4,true).whenComplete((chunk,error)->{
            if(closed||!plugin.isEnabled())return;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(closed)return;
                if(error!=null){find(piece,center,tries-1);return;}
                int y=world.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES)+1;
                Block block=world.getBlockAt(x,Math.min(y,world.getMaxHeight()-1),z);
                if(y>=world.getMaxHeight()-1||!LootDrops.safe(block)){find(piece,center,tries-1);return;}
                place(piece,block);searching=false;resume();
            });
        });
    }
    void place(String piece,Block block) {
        String path="pieces."+piece;
        state.set(path+".location",block.getLocation());state.set(path+".status","placing");save();
        block.setType(Material.CHEST,false);Chest chest=(Chest)block.getState();
        chest.getPersistentDataContainer().set(chestKey,PersistentDataType.STRING,state.getString("event")+":"+piece);
        chest.customName(Component.text("Warlord's "+piece,NamedTextColor.LIGHT_PURPLE));chest.update(true,false);
        chest.getBlockInventory().setItem(13,Objects.requireNonNull(state.getItemStack(path+".item")).clone());
        state.set(path+".status","ready");save();
        plugin.getLogger().info("Warlord hunt "+piece+" ready at "+coordinates(block.getLocation()));
    }
    static String coordinates(Location l){return l.getWorld().getName()+" | X: "+l.getBlockX()+" Y: "+l.getBlockY()+" Z: "+l.getBlockZ();}
    void reveal(Player player,String piece) {
        Location l=state.getLocation("pieces."+piece+".location");
        if(l==null||!state.getString("pieces."+piece+".status","").equals("ready")){player.sendMessage("That relic is not waiting in a chest. Use /warlordevent to check its status.");return;}
        Bukkit.broadcast(Component.text("Warlord's "+piece+" has been revealed! "+coordinates(l),NamedTextColor.LIGHT_PURPLE));
        state.set("pieces."+piece+".revealed",true);save();
    }
    void open(Player player) {
        Menu holder=new Menu();holder.inventory=Bukkit.createInventory(holder,27,Component.text("Warlord's Purge"));
        for(int i=0;i<WarlordRelics.PIECES.size();i++) {
            String piece=WarlordRelics.PIECES.get(i);ItemStack icon=relics.create(piece);var meta=icon.getItemMeta();
            String status=state.getString("pieces."+piece+".status","No hunt yet");
            Location l=state.getLocation("pieces."+piece+".location");
            meta.lore(List.of(Component.text("Status: "+status,NamedTextColor.GRAY),Component.text(l==null?"Awaiting location":coordinates(l),NamedTextColor.DARK_PURPLE),Component.text("Click to reveal coordinates publicly",NamedTextColor.YELLOW)));
            icon.setItemMeta(meta);holder.inventory.setItem(10+i,icon);
        }
        ItemStack preview=new ItemStack(Material.ENDER_EYE);var meta=preview.getItemMeta();meta.displayName(Component.text("Preview floating gear animation"));preview.setItemMeta(meta);holder.inventory.setItem(22,preview);
        player.openInventory(holder.inventory);
    }
    void retry(){resume();}
    @EventHandler(priority=EventPriority.HIGHEST) public void menu(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu))return;
        event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player player)||!player.hasPermission("shocksmp.events.admin"))return;
        int slot=event.getRawSlot();if(slot>=10&&slot<=16)reveal(player,WarlordRelics.PIECES.get(slot-10));
        if(slot==22){player.closeInventory();preview(player);}
    }
    @EventHandler public void menuDrag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    private String piece(Block block) {
        for(String piece:WarlordRelics.PIECES) {
            Location location=state.getLocation("pieces."+piece+".location");
            if(location!=null&&location.equals(block.getLocation())&&!state.getString("pieces."+piece+".status","").equals("claimed"))return piece;
        }return null;
    }
    private boolean protectedBlock(Block block){return piece(block)!=null;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakChest(BlockBreakEvent event){if(protectedBlock(event.getBlock()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void adjacentChest(BlockPlaceEvent event){
        if(event.getBlock().getType()!=Material.CHEST)return;
        for(BlockFace face:List.of(BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST))
            if(protectedBlock(event.getBlock().getRelative(face)))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(EntityExplodeEvent event){event.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(BlockExplodeEvent event){event.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonExtendEvent event){if(event.getBlocks().stream().anyMatch(this::protectedBlock))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonRetractEvent event){if(event.getBlocks().stream().anyMatch(this::protectedBlock))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopper(InventoryMoveItemEvent event){
        if(event.getSource().getLocation()!=null&&protectedBlock(event.getSource().getLocation().getBlock())||event.getDestination().getLocation()!=null&&protectedBlock(event.getDestination().getLocation().getBlock()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void chestClick(InventoryClickEvent event) {
        Inventory top=event.getView().getTopInventory();Location l=top.getLocation();if(l==null||!protectedBlock(l.getBlock()))return;
        // Public withdrawal only. Prevent deposits and swaps from hiding the prize or keeping an empty site alive.
        if(event.getClickedInventory()==event.getView().getBottomInventory()&&event.isShiftClick()
                ||event.getClickedInventory()==top&&Set.of(InventoryAction.PLACE_ALL,InventoryAction.PLACE_ONE,InventoryAction.PLACE_SOME,
                InventoryAction.SWAP_WITH_CURSOR,InventoryAction.HOTBAR_SWAP,InventoryAction.HOTBAR_MOVE_AND_READD).contains(event.getAction()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void chestDrag(InventoryDragEvent event){
        Inventory top=event.getView().getTopInventory();Location l=top.getLocation();
        if(l!=null&&protectedBlock(l.getBlock())&&event.getRawSlots().stream().anyMatch(i->i<top.getSize()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void taken(InventoryClickEvent event){check(event.getView().getTopInventory());}
    @EventHandler public void closed(InventoryCloseEvent event){check(event.getInventory());}
    @EventHandler public void opened(InventoryOpenEvent event){check(event.getInventory());}
    private void check(Inventory inventory) {
        Location l=inventory.getLocation();if(l==null)return;String piece=piece(l.getBlock());if(piece==null)return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(closed||state.getString("pieces."+piece+".status","").equals("claimed")||!inventory.isEmpty())return;
            state.set("pieces."+piece+".status","claimed");save();
            if(l.getBlock().getState() instanceof Chest chest&&chest.getPersistentDataContainer().has(chestKey,PersistentDataType.STRING))l.getBlock().setType(Material.AIR,false);
            Bukkit.broadcast(Component.text("The Warlord's "+piece+" has been found!",NamedTextColor.LIGHT_PURPLE));
        });
    }
    private void save(){try{EventFiles.save(file,state);}catch(IOException e){throw new IllegalStateException("Cannot save Warlord hunt",e);}}
    public void close(){closed=true;animation.close();save();HandlerList.unregisterAll(this);}
}

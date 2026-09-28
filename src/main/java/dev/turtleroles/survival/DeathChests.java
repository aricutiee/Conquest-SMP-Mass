package dev.turtleroles.survival;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Public physical double chests with a saved recovery copy and per-chest expiry. */
public final class DeathChests implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final java.util.function.LongSupplier clock;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String,Grave> graves=new LinkedHashMap<>();
    private final Map<String,String> blocks=new HashMap<>();
    private final Set<String> loading=new HashSet<>();
    private BukkitTask ticker;
    private static final class Grave {
        String id; Location left; long expires; ItemStack[] saved;
        Block a(){return left.getBlock();} Block b(){return a().getRelative(1,0,0);}
    }
    public DeathChests(JavaPlugin plugin) { this(plugin,System::currentTimeMillis); }
    DeathChests(JavaPlugin plugin,java.util.function.LongSupplier clock) {
        this.clock=clock;
        this.plugin=plugin;file=new File(plugin.getDataFolder(),"death-chests.yml");
        data=YamlConfiguration.loadConfiguration(file);
        for(String id:data.getKeys(false)) {
            Location location=data.getLocation(id+".location");
            if(location==null||location.getWorld()==null){plugin.getLogger().warning("Death chest world unavailable: "+id);continue;}
            Grave grave=new Grave();grave.id=id;grave.left=location;grave.expires=data.getLong(id+".expires");
            grave.saved=readItems(data.getList(id+".items",List.of()));graves.put(id,grave);index(grave);
        }
    }
    static ItemStack[] readItems(List<?> list) {
        ItemStack[] result=new ItemStack[54];
        for(int i=0;i<Math.min(54,list.size());i++)if(list.get(i) instanceof ItemStack item)result[i]=item.clone();
        return result;
    }
    static String key(Block block){return block.getWorld().getUID()+":"+block.getX()+":"+block.getY()+":"+block.getZ();}
    private void index(Grave grave){blocks.put(key(grave.a()),grave.id);blocks.put(key(grave.b()),grave.id);}
    private boolean owns(Block block){return blocks.containsKey(key(block));}
    public void start(){
        Bukkit.getPluginManager().registerEvents(this,plugin);
        ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
    }
    private boolean save() {
        try {
            Files.createDirectories(file.toPath().getParent());
            Path temp=file.toPath().resolveSibling(file.getName()+".tmp");Files.writeString(temp,data.saveToString());
            try{Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(IOException e){plugin.getLogger().severe("Could not save death chests: "+e.getMessage());return false;}
    }
    private void write(Grave grave){
        data.set(grave.id+".location",grave.left);data.set(grave.id+".expires",grave.expires);
        data.set(grave.id+".items",Arrays.asList(grave.saved));
    }
    private Location findSpace(Location origin) {
        World world=origin.getWorld();
        int y=Math.max(world.getMinHeight()+1,Math.min(world.getMaxHeight()-2,origin.getBlockY()));
        if(origin.getY()<world.getMinHeight())y=world.getHighestBlockYAt(origin)+1;
        for(int rise=0;rise<24;rise++)for(int radius=0;radius<=6;radius++)
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
                if(y+rise>=world.getMaxHeight()-1)continue;
                Block a=world.getBlockAt(origin.getBlockX()+dx,y+rise,origin.getBlockZ()+dz),b=a.getRelative(1,0,0);
                if(!a.getType().isAir()||!b.getType().isAir()||!world.getWorldBorder().isInside(a.getLocation())||!world.getWorldBorder().isInside(b.getLocation()))continue;
                if(a.getRelative(0,1,0).getType().isOccluding()||b.getRelative(0,1,0).getType().isOccluding())continue;
                boolean nearChest=false;
                for(Block block:List.of(a,b))for(BlockFace face:List.of(BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST))
                    if(block.getRelative(face).getType()==Material.CHEST)nearChest=true;
                if(!nearChest)return a.getLocation();
            }
        return null;
    }
    private void place(Grave grave) {
        Block a=grave.a(),b=grave.b();
        for(Block block:List.of(a,b)) {
            block.setType(Material.CHEST,false);
            Chest chest=(Chest)block.getBlockData();chest.setFacing(BlockFace.NORTH);chest.setType(block.equals(a)?Chest.Type.LEFT:Chest.Type.RIGHT);block.setBlockData(chest,false);
        }
        for(int half=0;half<2;half++) {
            org.bukkit.block.Chest chest=(org.bukkit.block.Chest)(half==0?a:b).getState();
            chest.customName(Component.text("Public Death Chest",NamedTextColor.LIGHT_PURPLE));chest.update(true,false);
            chest.getBlockInventory().setContents(Arrays.copyOfRange(grave.saved,half*27,half*27+27));
        }
    }
    private ItemStack[] contents(Grave grave) {
        ItemStack[] result=new ItemStack[54];
        for(int half=0;half<2;half++) {
            Block block=half==0?grave.a():grave.b();
            ItemStack[] items=block.getState() instanceof org.bukkit.block.Chest chest?chest.getBlockInventory().getContents():Arrays.copyOfRange(grave.saved,half*27,half*27+27);
            for(int i=0;i<27;i++)result[half*27+i]=items[i]==null?null:items[i].clone();
        }
        return result;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void death(PlayerDeathEvent event) {
        if(event.getKeepInventory()||event.getDrops().isEmpty())return;
        List<ItemStack> remaining=new ArrayList<>(event.getDrops());
        while(!remaining.isEmpty()) {
            Location space=findSpace(event.getEntity().getLocation());
            if(space==null){plugin.getLogger().warning("No safe air space for death chest; remaining drops retained for "+event.getEntity().getName());break;}
            Grave grave=new Grave();grave.id=UUID.randomUUID().toString();grave.left=space;grave.expires=clock.getAsLong()+600_000;
            int count=Math.min(54,remaining.size());grave.saved=readItems(remaining.subList(0,count));write(grave);
            if(!save()){data.set(grave.id,null);break;}
            place(grave);graves.put(grave.id,grave);index(grave);
            remaining.subList(0,count).clear();
            event.getEntity().sendMessage(Component.text("Your public death chest: "+space.getBlockX()+", "+space.getBlockY()+", "+space.getBlockZ()+" (10 minutes).",NamedTextColor.LIGHT_PURPLE));
        }
        event.getDrops().clear();event.getDrops().addAll(remaining);
    }
    void tick() {
        for(Grave grave:new ArrayList<>(graves.values())) {
            boolean expired=clock.getAsLong()>=grave.expires;
            World world=grave.left.getWorld();
            if(!world.isChunkLoaded(grave.left.getBlockX()>>4,grave.left.getBlockZ()>>4)) {
                if(expired && loading.add(grave.id))world.getChunkAtAsync(grave.left).whenComplete((chunk,error)-> {
                    if(plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{loading.remove(grave.id);if(error==null && graves.containsKey(grave.id))reconcile(grave);});
                });
                continue;
            }
            reconcile(grave);
        }
    }
    private void reconcile(Grave grave) {
        ItemStack[] latest=contents(grave);
        boolean changed=!Arrays.equals(latest,grave.saved);grave.saved=latest;
        if(changed){write(grave);if(!save())return;}
        boolean expired=clock.getAsLong()>=grave.expires;
        boolean empty=Arrays.stream(grave.saved).allMatch(i->i==null||i.getType().isAir());
        if(expired||empty){remove(grave,expired);return;}
        if(grave.a().getType()!=Material.CHEST||grave.b().getType()!=Material.CHEST) {
            // Recover after non-event edits. Never overwrite another container or a solid build.
            if((grave.a().getType().isAir()||grave.a().getType()==Material.CHEST)&&(grave.b().getType().isAir()||grave.b().getType()==Material.CHEST))place(grave);
        }
    }
    private void remove(Grave grave,boolean drop) {
        for(Player player:Bukkit.getOnlinePlayers())if(inventoryGrave(player.getOpenInventory().getTopInventory())==grave)player.closeInventory();
        // Refresh after viewers close, then persist deletion before releasing contents.
        grave.saved=contents(grave);data.set(grave.id,null);
        if(!save()){write(grave);return;}
        graves.remove(grave.id);blocks.remove(key(grave.a()));blocks.remove(key(grave.b()));
        for(Block block:List.of(grave.a(),grave.b()))if(block.getState() instanceof org.bukkit.block.Chest chest){chest.getBlockInventory().clear();block.setType(Material.AIR,false);}
        if(drop)for(ItemStack item:grave.saved)if(item!=null&&!item.getType().isAir())
            grave.left.getWorld().dropItemNaturally(grave.left.clone().add(.5,.5,.5),item,entity->entity.setTicksLived(1));
    }
    private Grave inventoryGrave(Inventory inventory) {
        if(inventory==null)return null;
        Location loc=inventory.getLocation();return loc==null?null:graves.get(blocks.get(key(loc.getBlock())));
    }
    private void changed(Inventory inventory) {
        Grave grave=inventoryGrave(inventory);if(grave==null)return;
        Bukkit.getScheduler().runTask(plugin,()->{if(graves.containsKey(grave.id))reconcile(grave);});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void click(InventoryClickEvent e){changed(e.getView().getTopInventory());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void drag(InventoryDragEvent e){changed(e.getView().getTopInventory());}
    @EventHandler public void closeInventory(InventoryCloseEvent e){changed(e.getInventory());}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void hopper(InventoryMoveItemEvent e){if(inventoryGrave(e.getSource())!=null||inventoryGrave(e.getDestination())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void breakBlock(BlockBreakEvent e){if(owns(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void explosion(EntityExplodeEvent e){e.blockList().removeIf(this::owns);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void explosion(BlockExplodeEvent e){e.blockList().removeIf(this::owns);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(b->owns(b)||owns(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void piston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(b->owns(b)||owns(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void fluid(BlockFromToEvent e){if(owns(e.getToBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void entity(EntityChangeBlockEvent e){if(owns(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void burn(BlockBurnEvent e){if(owns(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void place(BlockPlaceEvent e){
        for(BlockFace face:List.of(BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.DOWN))
            if(owns(e.getBlock().getRelative(face))){e.setCancelled(true);return;}
    }
    @Override public void close(){
        if(ticker!=null)ticker.cancel();
        for(Grave grave:graves.values()) {
            if(grave.left.getWorld().isChunkLoaded(grave.left.getBlockX()>>4,grave.left.getBlockZ()>>4))grave.saved=contents(grave);
            write(grave);
        }
        save();HandlerList.unregisterAll(this);
    }
}

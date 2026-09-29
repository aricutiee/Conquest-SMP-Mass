package dev.turtleroles.service;

import dev.turtleroles.TurtleRolesPlugin;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import java.nio.file.*;
import java.util.*;

/** Bought spawners only. Vanilla spawner blocks are never adopted automatically. */
public final class VirtualSpawners implements Listener,AutoCloseable {
    private static final int MAX=500, INTERVAL=120, XP_CAP=1_000_000;
    private final TurtleRolesPlugin plugin;
    private final NamespacedKey typeKey,countKey,idKey;
    private final Path file;
    private final Map<String,Farm> farms=new LinkedHashMap<>();
    private BukkitTask task;
    private final Random random=new Random();
    private static final class Farm implements InventoryHolder {
        String id=UUID.randomUUID().toString();
        final String key;final SpawnerKind kind;final Inventory inventory;
        int count,xp,seconds;
        Farm(String key,SpawnerKind kind,int count){this.key=key;this.kind=kind;this.count=count;inventory=Bukkit.createInventory(this,54,text(kind.title()+" loot"));}
        public Inventory getInventory(){return inventory;}
    }
    private static final class Shop implements InventoryHolder {
        final UUID owner;final Map<Integer,SpawnerKind> offers=new HashMap<>();Inventory inventory;
        Shop(UUID owner){this.owner=owner;}public Inventory getInventory(){return inventory;}
    }
    public VirtualSpawners(TurtleRolesPlugin plugin){
        this.plugin=plugin;typeKey=new NamespacedKey(plugin,"virtual_spawner");countKey=new NamespacedKey(plugin,"virtual_spawner_count");idKey=new NamespacedKey(plugin,"virtual_spawner_id");file=plugin.getDataFolder().toPath().resolve("virtual-spawners.yml");
    }
    public void start() throws Exception {
        if(Files.exists(file)) {
            var data=new YamlConfiguration();data.load(file.toFile());var section=data.getConfigurationSection("farms");
            if(section!=null)for(String key:section.getKeys(false)) {
                String path="farms."+key+".";var kind=SpawnerKind.valueOf(data.getString(path+"type"));int count=data.getInt(path+"count");
                if(count<1||count>MAX)throw new IllegalStateException("Invalid virtual spawner stack "+key);
                Farm farm=new Farm(key,kind,count);farm.id=data.getString(path+"id",farm.id);farm.xp=Math.clamp(data.getInt(path+"xp"),0,XP_CAP);farm.seconds=Math.clamp(data.getInt(path+"seconds"),0,INTERVAL-1);
                var contents=data.getList(path+"items",List.of());for(int i=0;i<Math.min(54,contents.size());i++)if(contents.get(i) instanceof ItemStack item)farm.inventory.setItem(i,item);
                farms.put(key,farm);
            }
        }
        Bukkit.getPluginManager().registerEvents(this,plugin);task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
    }
    static Component text(String s){return Component.text(ConquestMotd.smallCaps(s),TextColor.color(0xB477FF)).decoration(TextDecoration.ITALIC,false);}
    public ItemStack item(SpawnerKind kind,int count){
        if(count<1||count>MAX)throw new IllegalArgumentException();
        ItemStack item=new ItemStack(Material.SPAWNER);var meta=(BlockStateMeta)item.getItemMeta();
        meta.setMaxStackSize(1);meta.displayName(text(kind.title()+" virtual spawner x"+count));
        var state=(CreatureSpawner)meta.getBlockState();state.setSpawnedType(kind.entity());state.setSpawnCount(0);meta.setBlockState(state);
        meta.getPersistentDataContainer().set(typeKey,PersistentDataType.STRING,kind.name());meta.getPersistentDataContainer().set(countKey,PersistentDataType.INTEGER,count);
        List<Component> lore=new ArrayList<>();for(String line:kind.lootLore())lore.add(text(line));
        lore.add(text("Right-click to view its 54-slot storage"));lore.add(text("Sneak + right-click to collect stored XP"));
        lore.add(text("Silk Touch pickaxe: recover the stack"));lore.add(text("Right-click same type to stack, max 500"));meta.lore(lore);item.setItemMeta(meta);return item;
    }
    private SpawnerKind type(ItemStack item){
        if(item==null||item.getType()!=Material.SPAWNER||!item.hasItemMeta())return null;
        String kind=item.getItemMeta().getPersistentDataContainer().get(typeKey,PersistentDataType.STRING);
        try{return kind==null?null:SpawnerKind.valueOf(kind);}catch(IllegalArgumentException ex){return null;}
    }
    private int count(ItemStack item){return item.getItemMeta().getPersistentDataContainer().getOrDefault(countKey,PersistentDataType.INTEGER,1);}
    private static String key(Block b){return b.getWorld().getUID()+"_"+b.getX()+"_"+b.getY()+"_"+b.getZ();}
    private Block block(String key){String[] a=key.split("_");World w=Bukkit.getWorld(UUID.fromString(a[0]));if(w==null)return null;int x=Integer.parseInt(a[1]),y=Integer.parseInt(a[2]),z=Integer.parseInt(a[3]);return w.isChunkLoaded(x>>4,z>>4)?w.getBlockAt(x,y,z):null;}
    private Farm farm(Block block){
        Farm f=farms.get(key(block));
        return f!=null && block.getState() instanceof CreatureSpawner spawner
            && f.id.equals(spawner.getPersistentDataContainer().get(idKey,PersistentDataType.STRING))?f:null;
    }
    private boolean protectedFor(Player p,Block b){return plugin.survival()!=null&&plugin.survival().spawnArea().inside(b.getLocation())&&!dev.turtleroles.service.GameplayBypass.allowed(plugin,p);}
    private boolean save(){
        var data=new YamlConfiguration();for(Farm f:farms.values()){String p="farms."+f.key+".";data.set(p+"id",f.id);data.set(p+"type",f.kind.name());data.set(p+"count",f.count);data.set(p+"xp",f.xp);data.set(p+"seconds",f.seconds);data.set(p+"items",Arrays.asList(f.inventory.getContents()));}
        try{AtomicYaml.save(data,file);return true;}catch(Exception ex){plugin.getLogger().severe("Virtual spawners could not save: "+ex.getMessage());return false;}
    }
    private void tick(){
        boolean changed=false;
        for(Farm farm:farms.values()) {
            Block b=block(farm.key);if(b==null||farm(b)!=farm)continue;
            if(++farm.seconds<INTERVAL)continue;farm.seconds=0;
            var batch=farm.kind.roll(farm.count,random);ItemStack[] next=fit(farm.inventory.getContents(),batch.items());
            if(next==null||farm.xp>XP_CAP-batch.xp())continue;
            farm.inventory.setContents(next);farm.xp+=batch.xp();changed=true;
        }
        if(changed)save();
    }
    static ItemStack[] fit(ItemStack[] contents,Map<Material,Integer> amounts){
        ItemStack[] next=Arrays.stream(contents).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
        for(var e:amounts.entrySet()) {
            int left=e.getValue();while(left>0){int n=Math.min(left,e.getKey().getMaxStackSize());next=SpawnNpcs.delivery(next,new ItemStack(e.getKey(),n));if(next==null)return null;left-=n;}
        }return next;
    }
    private void configure(Block b,Farm f){if(b.getState() instanceof CreatureSpawner spawner){spawner.getPersistentDataContainer().set(idKey,PersistentDataType.STRING,f.id);spawner.setSpawnedType(f.kind.entity());spawner.setSpawnCount(0);spawner.setDelay(32767);spawner.update(true,false);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){
        SpawnerKind kind=type(e.getItemInHand());if(kind==null)return;
        int n=count(e.getItemInHand());String k=key(e.getBlock());
        if(n<1||n>MAX||farms.containsKey(k)||protectedFor(e.getPlayer(),e.getBlock())){e.setCancelled(true);return;}
        Farm f=new Farm(k,kind,n);farms.put(k,f);if(!save()){farms.remove(k);e.setCancelled(true);return;}
        configure(e.getBlock(),f);
        // Respect a later protection listener that cancels the placement.
        Bukkit.getScheduler().runTask(plugin,()->{if(e.isCancelled()||e.getBlock().getType()!=Material.SPAWNER){farms.remove(k);save();}});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spawn(SpawnerSpawnEvent e){if(farm(e.getSpawner().getBlock())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getClickedBlock()==null)return;
        Farm f=farm(e.getClickedBlock());if(f==null)return;
        if(e.useInteractedBlock()==Event.Result.DENY||protectedFor(e.getPlayer(),e.getClickedBlock()))return;
        e.setCancelled(true);Player p=e.getPlayer();ItemStack held=p.getInventory().getItemInMainHand();SpawnerKind incoming=type(held);
        if(incoming!=null){
            int amount=count(held);if(incoming!=f.kind){p.sendMessage(text("Only matching spawner types can stack."));return;}
            if(amount<1||amount>MAX||f.count+amount>MAX){p.sendMessage(text("A stack can contain at most 500 spawners."));return;}
            f.count+=amount;if(!save()){f.count-=amount;p.sendMessage(text("Could not save. Your spawner was not consumed."));return;}
            held.subtract(1);p.getInventory().setItemInMainHand(held);p.saveData();p.sendMessage(text("Stack now contains "+f.count+" "+f.kind.title()+" spawners."));return;
        }
        if(p.isSneaking()){
            int xp=f.xp;f.xp=0;if(!save()){f.xp=xp;return;}p.giveExp(xp);p.saveData();p.sendMessage(text("Collected "+xp+" XP."));return;
        }
        p.sendMessage(text("Stack: "+f.count+" | XP: "+f.xp+" | Next cycle: "+(INTERVAL-f.seconds)+"s"));
        p.sendMessage(text("Click loot to take it. Sneak + right-click the spawner to collect XP."));p.openInventory(f.inventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void mine(BlockBreakEvent e){
        String k=key(e.getBlock());Farm f=farm(e.getBlock());if(f==null)return;e.setCancelled(true);
        if(protectedFor(e.getPlayer(),e.getBlock()))return;
        ItemStack tool=e.getPlayer().getInventory().getItemInMainHand();
        if(!tool.getType().name().endsWith("_PICKAXE")||!tool.containsEnchantment(Enchantment.SILK_TOUCH)){e.getPlayer().sendMessage(text("Use a Silk Touch pickaxe to recover this virtual spawner."));return;}
        if(!f.inventory.isEmpty()||f.xp>0){e.getPlayer().sendMessage(text("Collect all stored loot and XP before picking up the spawner."));return;}
        farms.remove(k);if(!save()){farms.put(k,f);return;}
        for(var viewer:List.copyOf(f.inventory.getViewers()))viewer.closeInventory();e.getBlock().setType(Material.AIR,false);
        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(.5,.5,.5),item(f.kind,f.count));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
        Inventory top=e.getView().getTopInventory();if(!(e.getWhoClicked() instanceof Player p))return;
        if(top.getHolder() instanceof Shop shop){boolean blocked=e.isCancelled();e.setCancelled(true);if(!blocked&&shop.owner.equals(p.getUniqueId())&&shop.offers.containsKey(e.getRawSlot()))buy(p,shop.offers.get(e.getRawSlot()));return;}
        if(!(top.getHolder() instanceof Farm f))return;boolean blocked=e.isCancelled();e.setCancelled(true);
        Block b=block(f.key);if(blocked||b==null||farm(b)!=f||farms.get(f.key)!=f||protectedFor(p,b)||e.getRawSlot()<0||e.getRawSlot()>=54)return;
        ItemStack selected=top.getItem(e.getRawSlot());if(selected==null)return;
        ItemStack[] delivered=SpawnNpcs.delivery(p.getInventory().getStorageContents(),selected);if(delivered==null){p.sendMessage(text("Not enough inventory space."));return;}
        top.setItem(e.getRawSlot(),null);if(!save()){top.setItem(e.getRawSlot(),selected);return;}p.getInventory().setStorageContents(delivered);p.saveData();
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Farm||e.getView().getTopInventory().getHolder() instanceof Shop)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void explode(EntityExplodeEvent e){e.blockList().removeIf(b->farm(b)!=null);}
    @EventHandler(priority=EventPriority.HIGHEST) public void explode(BlockExplodeEvent e){e.blockList().removeIf(b->farm(b)!=null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(b->farm(b)!=null))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(b->farm(b)!=null))e.setCancelled(true);}
    private void buy(Player p,SpawnerKind kind){
        if(!ClientCompatibility.authenticated(p)||p.isDead()||plugin.combat().tagged(p))return;
        var next=SpawnNpcs.delivery(p.getInventory().getStorageContents(),item(kind,1));if(next==null){p.sendMessage(text("Not enough inventory space. No shards spent."));return;}
        try{if(!plugin.races().store().spend(p.getUniqueId(),kind.price)){p.sendMessage(text("You do not have enough shards."));return;}}
        catch(Exception ex){p.sendMessage(text("Purchase could not be saved. No shards spent."));return;}
        p.getInventory().setStorageContents(next);p.saveData();p.sendMessage(text("Bought a "+kind.title()+" spawner for "+kind.price+" shards."));shop(p);
    }
    public void shop(Player p){
        Shop menu=new Shop(p.getUniqueId());menu.inventory=Bukkit.createInventory(menu,54,text("Spawners • Shard Shop"));
        for(int slot=0;slot<45;slot++){ItemStack pane=new ItemStack(((slot/9+slot%9)%2==0)?Material.PURPLE_STAINED_GLASS_PANE:Material.BLACK_STAINED_GLASS_PANE);var m=pane.getItemMeta();m.displayName(Component.text(" "));m.setHideTooltip(true);pane.setItemMeta(m);menu.inventory.setItem(slot,pane);}
        int[] slots={0,2,4,6,8,18,20,22,24,26,36};int i=0;
        for(SpawnerKind kind:SpawnerKind.values()){int slot=slots[i++];menu.offers.put(slot,kind);ItemStack shown=item(kind,1);var m=shown.getItemMeta();List<Component> lore=new ArrayList<>(m.lore());lore.add(text("Price: "+kind.price+" shards"));lore.add(text("Click to buy one"));m.lore(lore);shown.setItemMeta(m);menu.inventory.setItem(slot,shown);}
        ItemStack balance=new ItemStack(Material.AMETHYST_SHARD);var meta=balance.getItemMeta();meta.displayName(text("Your shards: "+plugin.races().state(p).shards));balance.setItemMeta(meta);menu.inventory.setItem(49,balance);p.openInventory(menu.inventory);
    }
    @Override public void close(){if(task!=null)task.cancel();for(Player p:Bukkit.getOnlinePlayers()){var top=p.getOpenInventory().getTopInventory();if(top!=null&&(top.getHolder() instanceof Farm||top.getHolder() instanceof Shop))p.closeInventory();}save();HandlerList.unregisterAll(this);}
}

package dev.turtleroles.events;

import dev.turtleroles.service.GameplayBypass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.function.*;

/** Only encounter prizes and the active boss's temporary mace are usable. */
final class JuggernautMaces implements Listener,AutoCloseable {
    private static final NamespacedKey INSTANCE=new NamespacedKey("shocksmp","shock_mace_instance");
    private static final NamespacedKey ISSUED=new NamespacedKey("shocksmp","event_reward_issued");
    private static final NamespacedKey EVENT=new NamespacedKey("shocksmp","event_id");
    private final JavaPlugin plugin;
    private final ShockMace factory;
    private final Predicate<ItemStack> activeKit;
    private final LongSupplier clock;
    private final File file;
    private final YamlConfiguration data;
    private final BukkitTask task;
    private final Map<String,MaceRise> rises=new HashMap<>();
    private boolean dirty;
    private int ticks;
    JuggernautMaces(JavaPlugin plugin,ShockMace factory,Predicate<ItemStack> activeKit) {
        this(plugin,factory,activeKit,System::currentTimeMillis);
    }
    JuggernautMaces(JavaPlugin plugin,ShockMace factory,Predicate<ItemStack> activeKit,LongSupplier clock) {
        this.plugin=plugin;this.factory=factory;this.activeKit=activeKit;this.clock=clock;
        file=new File(plugin.getDataFolder(),"juggernaut-maces.yml");data=YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,5);
        Bukkit.getScheduler().runTask(plugin,()->{
            for(World world:Bukkit.getWorlds())for(Chunk chunk:world.getLoadedChunks())inspect(chunk);
            Bukkit.getOnlinePlayers().forEach(this::deliverPending);
            var all=data.getConfigurationSection("maces");
            if(all!=null)for(String id:all.getKeys(false))if(data.getBoolean("maces."+id+".ground-pending")&&!data.getBoolean("maces."+id+".ground-dropping"))startRise(id);
        });
    }
    static long glowDeadline(long awardedAt){return Math.addExact(awardedAt,180_000L);}
    static long remaining(long deadline,long now){return Math.max(0,deadline-now);}
    private String token(ItemStack item) {
        if(!factory.isGenuine(item))return null;
        return item.getItemMeta().getPersistentDataContainer().get(INSTANCE,PersistentDataType.STRING);
    }
    boolean earned(ItemStack item) {
        String token=token(item);if(token==null)return false;
        if(data.contains("maces."+token))return true;
        // Preserve genuine prizes already issued by earlier Juggernaut encounters.
        var tags=item.getItemMeta().getPersistentDataContainer();
        if(tags.has(ISSUED,PersistentDataType.BYTE)&&tags.has(EVENT,PersistentDataType.STRING)) {
            data.set("maces."+token+".event",tags.get(EVENT,PersistentDataType.STRING));
            data.set("maces."+token+".expires",0L);
            if(save())return true;
            data.set("maces."+token,null);
        }
        return false;
    }
    boolean permitted(ItemStack item){return item==null||item.getType()!=Material.MACE||earned(item)||activeKit.test(item);}
    void dropAnimated(UUID event,Location location){
        if(event==null||data.contains("events."+event))return;
        ItemStack prize=factory.create();String id=token(prize);
        var meta=prize.getItemMeta();meta.getPersistentDataContainer().set(ISSUED,PersistentDataType.BYTE,(byte)1);
        meta.getPersistentDataContainer().set(EVENT,PersistentDataType.STRING,event.toString());prize.setItemMeta(meta);
        String path="maces."+id;
        data.set("events."+event,id);data.set(path+".event",event.toString());data.set(path+".expires",0L);
        data.set(path+".item",prize);data.set(path+".ground-location",location);data.set(path+".ground-pending",true);
        if(!save()){data.set("events."+event,null);data.set(path,null);throw new IllegalStateException("Could not save the Juggernaut mace reward");}
        startRise(id);
    }
    private void startRise(String id){
        String path="maces."+id;Location location=data.getLocation(path+".ground-location");ItemStack item=data.getItemStack(path+".item");
        if(location==null||location.getWorld()==null||item==null||rises.containsKey(id))return;
        MaceRise rise=new MaceRise(plugin,location,item,()->releaseGround(id));rises.put(id,rise);
        try{rise.start();}catch(RuntimeException failure){plugin.getLogger().warning("Mace animation failed; releasing saved reward: "+failure.getMessage());rise.close();}
    }
    private void releaseGround(String id){
        rises.remove(id);String path="maces."+id;
        if(!data.getBoolean(path+".ground-pending")||data.getBoolean(path+".ground-dropping"))return;
        Location location=data.getLocation(path+".ground-location");ItemStack item=data.getItemStack(path+".item");
        if(location==null||location.getWorld()==null||item==null)return;
        data.set(path+".ground-dropping",true);data.set(path+".expires",glowDeadline(clock.getAsLong()));
        if(!save()){data.set(path+".ground-dropping",false);return;}
        // Persist a transfer marker before materializing, avoiding duplicate prizes after a crash.
        Item drop=location.getWorld().dropItem(location.clone().add(0,1,0),item.clone());drop.setGlowing(true);drop.setInvulnerable(true);drop.setPickupDelay(10);
        data.set(path+".ground-pending",false);data.set(path+".ground-dropping",false);data.set(path+".item",null);save();
        Bukkit.broadcast(Component.text("The Juggernaut's Mace has fallen! Anyone can claim it.",NamedTextColor.LIGHT_PURPLE));
    }
    void award(UUID event,DamageRanking.Entry winner,Location deathLocation) {
        if(event==null||data.contains("events."+event))return;
        ItemStack prize=factory.create();String id=token(prize);
        long expires=glowDeadline(clock.getAsLong());
        var meta=prize.getItemMeta();meta.getPersistentDataContainer().set(ISSUED,PersistentDataType.BYTE,(byte)1);
        meta.getPersistentDataContainer().set(EVENT,PersistentDataType.STRING,event.toString());prize.setItemMeta(meta);
        data.set("events."+event,id);data.set("maces."+id+".event",event.toString());
        data.set("maces."+id+".expires",expires);
        data.set("maces."+id+".pending",winner.player().toString());
        data.set("maces."+id+".item",prize);
        if(!save()) {data.set("events."+event,null);data.set("maces."+id,null);throw new IllegalStateException("Mace award could not be saved");}
        for(Player viewer:Bukkit.getOnlinePlayers())viewer.showTitle(Title.title(
                Component.text(winner.name(),NamedTextColor.LIGHT_PURPLE),
                Component.text("Earned the Juggernaut's Mace!",NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(400),Duration.ofSeconds(4),Duration.ofMillis(600))));
        Bukkit.broadcast(Component.text(winner.name()+" dealt the most damage ("+String.format(Locale.ROOT,"%.1f",winner.damage())+") and earned the Juggernaut's Mace!",NamedTextColor.LIGHT_PURPLE));
        Player player=Bukkit.getPlayer(winner.player());
        if(player!=null&&player.isOnline()&&!player.isDead())deliver(id,player);
        else Bukkit.broadcast(Component.text("The mace is reserved for "+winner.name()+" and will be delivered when they return.",NamedTextColor.YELLOW));
    }
    private void deliver(String id,Player player) {
        ItemStack item=data.getItemStack("maces."+id+".item");if(item==null)return;
        data.set("maces."+id+".pending",null);
        data.set("maces."+id+".delivering",player.getUniqueId().toString());
        if(!save()){data.set("maces."+id+".pending",player.getUniqueId().toString());return;}
        // A crash during transfer leaves a reviewable record instead of automatically duplicating loot.
        if(!hasToken(player.getInventory(),id)&&!id.equals(token(player.getItemOnCursor()))) {
            if(Arrays.stream(player.getInventory().getStorageContents()).noneMatch(i->i==null||i.getType().isAir())) {
                Item drop=player.getWorld().dropItemNaturally(player.getLocation(),item.clone());drop.setGlowing(true);
                Bukkit.broadcast(Component.text(player.getName()+" has dropped the Juggernaut's Mace because their inventory is full!",NamedTextColor.YELLOW));
                expose(player,data.getLong("maces."+id+".expires"));
            }else {player.getInventory().addItem(item.clone());holder(player,item);player.saveData();}
        }
        data.set("maces."+id+".delivering",null);data.set("maces."+id+".item",null);save();
    }
    private boolean hasToken(Inventory inventory,String id){return Arrays.stream(inventory.getContents()).anyMatch(i->id.equals(token(i)));}
    private void expose(Player player,long deadline) {
        String path="glow."+player.getUniqueId();
        if(deadline>data.getLong(path)){data.set(path,deadline);dirty=true;}
        glow(player);
    }
    private void holder(Player player,ItemStack item) {
        if(!earned(item))return;
        String id=token(item),path="maces."+id;
        if(!player.getUniqueId().toString().equals(data.getString(path+".holder"))) {
            data.set(path+".holder",player.getUniqueId().toString());dirty=true;
            Bukkit.broadcast(Component.text(player.getName()+" is the new wielder of the Juggernaut's Mace!",NamedTextColor.LIGHT_PURPLE));
        }
        expose(player,data.getLong(path+".expires"));
    }
    private void glow(Player player) {
        long left=remaining(data.getLong("glow."+player.getUniqueId()),clock.getAsLong());
        if(left==0||player.isDead())return;
        PotionEffect external=player.getPotionEffect(PotionEffectType.GLOWING);
        int duration=(int)Math.min(10,(left+49)/50);
        // Short leases expire naturally. Never clear or shorten another plugin's glow.
        if(external==null||(!external.isInfinite()&&external.getDuration()<duration))
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,duration,0,false,false,false));
    }
    private void tick() {
        Set<String> carried=new HashSet<>();
        for(Player player:Bukkit.getOnlinePlayers()) {
            if(player.isDead())continue;
            if(!GameplayBypass.allowed(plugin,player))clean(player.getInventory(),"player "+player.getUniqueId());
            ItemStack oldCursor=player.getItemOnCursor(),cursor=GameplayBypass.allowed(plugin,player)?oldCursor:cleanItem(oldCursor,"cursor "+player.getUniqueId(),0);
            if(!Objects.equals(oldCursor,cursor))player.setItemOnCursor(cursor);
            for(int slot=0;slot<player.getInventory().getSize();slot++) {
                ItemStack item=player.getInventory().getItem(slot);
                if(!earned(item))continue;
                if(!carried.add(token(item))) {if(archive(item,"duplicate in "+player.getUniqueId()))player.getInventory().setItem(slot,null);continue;}
                holder(player,item);
            }
            if(earned(cursor)) {
                if(!carried.add(token(cursor))) {if(archive(cursor,"duplicate cursor"))player.setItemOnCursor(null);}
                else holder(player,cursor);
            }
            glow(player);
            if(ticks%20==0&&!GameplayBypass.allowed(plugin,player))clean(player.getEnderChest(),"ender chest "+player.getUniqueId());
        }
        ticks++;
        if(dirty)save();
    }
    /** Archive before removing, including nested shulkers and bundles. */
    ItemStack cleanItem(ItemStack item,String source,int depth) {
        if(item==null||item.getType().isAir())return item;
        if(!permitted(item))return archive(item,source)?null:item;
        if(item.getType()==Material.MACE&&item.getAmount()>1) {
            ItemStack extra=item.clone();extra.setAmount(item.getAmount()-1);
            if(archive(extra,source+" stacked mace")){item=item.clone();item.setAmount(1);}
        }
        if(depth>16||!item.hasItemMeta())return item;
        ItemMeta meta=item.getItemMeta();boolean changed=false;
        if(meta instanceof BundleMeta bundle) {
            List<ItemStack> contents=new ArrayList<>();
            for(ItemStack child:bundle.getItems()){ItemStack clean=cleanItem(child,source+" bundle",depth+1);if(clean!=null)contents.add(clean);}
            if(!contents.equals(bundle.getItems())){bundle.setItems(contents);changed=true;}
        }
        if(meta instanceof BlockStateMeta block && block.getBlockState() instanceof Container container) {
            var inv=container.getInventory();for(int i=0;i<inv.getSize();i++){
                ItemStack old=inv.getItem(i),clean=cleanItem(old,source+" container",depth+1);
                if(!Objects.equals(old,clean)){inv.setItem(i,clean);changed=true;}
            }
            if(changed)block.setBlockState(container);
        }
        if(changed){item=item.clone();item.setItemMeta(meta);}return item;
    }
    private boolean archive(ItemStack item,String source) {
        File backup=new File(plugin.getDataFolder(),"mace-quarantine/"+clock.getAsLong()+"-"+UUID.randomUUID()+".yml");
        var entry=new YamlConfiguration();entry.set("source",source);entry.set("item",item);
        try {EventFiles.save(backup,entry);return true;}catch(IOException e){plugin.getLogger().severe("Cannot quarantine mace: "+e.getMessage());return false;}
    }
    private void clean(Inventory inv,String source) {
        for(int slot=0;slot<inv.getSize();slot++) {ItemStack item=inv.getItem(slot);if(item!=null){ItemStack clean=cleanItem(item,source,0);if(!Objects.equals(item,clean))inv.setItem(slot,clean);}}
    }
    private void inspect(Chunk chunk) {
        for(var block:chunk.getTileEntities())if(block instanceof Container c)clean(c.getInventory(),"container "+c.getLocation());
        for(Entity entity:chunk.getEntities())inspect(entity);
    }
    private void inspect(Entity entity) {
        if(entity instanceof Item drop) {
            ItemStack clean=cleanItem(drop.getItemStack(),"ground "+drop.getLocation(),0);
            if(clean==null)drop.remove();else {drop.setItemStack(clean);if(earned(clean))drop.setGlowing(true);}
        }else if(entity instanceof org.bukkit.inventory.InventoryHolder holder && !(entity instanceof Player))clean(holder.getInventory(),"entity "+entity.getUniqueId());
        if(entity instanceof LivingEntity living && !(entity instanceof Player)&&living.getEquipment()!=null) {
            var equipment=living.getEquipment();
            equipment.setItemInMainHand(cleanItem(equipment.getItemInMainHand(),"entity hand",0));
            equipment.setItemInOffHand(cleanItem(equipment.getItemInOffHand(),"entity offhand",0));
        }
        if(entity instanceof ItemFrame frame){ItemStack clean=cleanItem(frame.getItem(),"item frame",0);frame.setItem(clean);}
    }
    @EventHandler public void chunk(ChunkLoadEvent event){Bukkit.getScheduler().runTask(plugin,()->{if(event.getChunk().isLoaded())inspect(event.getChunk());});}
    @EventHandler public void entities(EntitiesLoadEvent event){event.getEntities().forEach(this::inspect);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spawn(ItemSpawnEvent event){
        ItemStack item=cleanItem(event.getEntity().getItemStack(),"item spawn",0);
        if(item==null)event.setCancelled(true);else {event.getEntity().setItemStack(item);if(earned(item))event.getEntity().setGlowing(true);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent event){if(GameplayBypass.allowed(plugin,event.getEntity()))return;
        if(!permitted(event.getItem().getItemStack())){event.setCancelled(true);inspect(event.getItem());}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void picked(EntityPickupItemEvent event){
        if(event.getEntity() instanceof Player player) {
            ItemStack picked=event.getItem().getItemStack().clone();
            Bukkit.getScheduler().runTask(plugin,()->{
                if(!event.isCancelled()&&event.getRemaining()==0&&!event.getItem().isValid()&&player.isOnline()&&earned(picked))holder(player,picked);
            });
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void drop(PlayerDropItemEvent event){
        if(earned(event.getItemDrop().getItemStack())){holder(event.getPlayer(),event.getItemDrop().getItemStack());event.getItemDrop().setGlowing(true);}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void craft(PrepareItemCraftEvent event){if(GameplayBypass.allowed(plugin,event.getView().getPlayer()))return;if(event.getInventory().getResult()!=null&&event.getInventory().getResult().getType()==Material.MACE)event.getInventory().setResult(null);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void crafted(CraftItemEvent event){if(GameplayBypass.allowed(plugin,event.getWhoClicked()))return;if(event.getRecipe().getResult().getType()==Material.MACE)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void crafter(CrafterCraftEvent event){if(event.getResult().getType()==Material.MACE)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void loot(LootGenerateEvent event){event.getLoot().removeIf(i->!permitted(i));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void trade(VillagerAcquireTradeEvent event){if(event.getRecipe().getResult().getType()==Material.MACE)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void attack(EntityDamageByEntityEvent event){if(GameplayBypass.allowed(plugin,event.getDamager()))return;if(event.getDamager() instanceof LivingEntity living&&living.getEquipment()!=null&&!permitted(living.getEquipment().getItemInMainHand()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent event){if(GameplayBypass.allowed(plugin,event.getPlayer()))return;if(!permitted(event.getItem()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void open(InventoryOpenEvent event){if(GameplayBypass.allowed(plugin,event.getPlayer()))return;if(!EventSystem.isEventMenu(event.getInventory()))clean(event.getInventory(),"opened inventory");}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void click(InventoryClickEvent event){if(GameplayBypass.allowed(plugin,event.getWhoClicked()))return;
        if(EventSystem.isEventMenu(event.getView().getTopInventory()))return;
        if(!permitted(event.getCurrentItem())||!permitted(event.getCursor()))event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin,()->{if(!EventSystem.isEventMenu(event.getView().getTopInventory()))clean(event.getView().getTopInventory(),"inventory click");});
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(InventoryMoveItemEvent event){if(!permitted(event.getItem())){event.setCancelled(true);clean(event.getSource(),"hopper source");}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopper(InventoryPickupItemEvent event){if(!permitted(event.getItem().getItemStack())){event.setCancelled(true);inspect(event.getItem());}}
    @EventHandler(priority=EventPriority.LOWEST) public void death(PlayerDeathEvent event){if(GameplayBypass.allowed(plugin,event.getEntity()))return;
        event.getDrops().replaceAll(i->cleanItem(i,"death drops",0));event.getDrops().removeIf(Objects::isNull);
    }
    private void deliverPending(Player player) {
        if(!player.isOnline()||player.isDead())return;
        var section=data.getConfigurationSection("maces");
        if(section!=null)for(String id:section.getKeys(false)) {
            if(player.getUniqueId().toString().equals(data.getString("maces."+id+".pending")))deliver(id,player);
            else if(player.getUniqueId().toString().equals(data.getString("maces."+id+".delivering")))
                plugin.getLogger().warning("Review interrupted mace delivery "+id+" to "+player.getName()+" in juggernaut-maces.yml; not automatically duplicated.");
        }
    }
    @EventHandler public void join(PlayerJoinEvent event){Bukkit.getScheduler().runTask(plugin,()->{
        Player player=event.getPlayer();if(!player.isOnline())return;deliverPending(player);
        glow(player);if(!GameplayBypass.allowed(plugin,player))clean(player.getEnderChest(),"joined ender inventory");
    });}
    @EventHandler public void respawn(PlayerRespawnEvent event){Bukkit.getScheduler().runTask(plugin,()->{deliverPending(event.getPlayer());glow(event.getPlayer());});}
    @EventHandler public void quit(PlayerQuitEvent event){save();}
    private boolean save(){try{EventFiles.save(file,data);dirty=false;return true;}catch(IOException e){plugin.getLogger().severe("Cannot save Juggernaut maces: "+e.getMessage());return false;}}
    public void close(){task.cancel();for(MaceRise rise:List.copyOf(rises.values()))rise.close();rises.clear();save();HandlerList.unregisterAll(this);}
}

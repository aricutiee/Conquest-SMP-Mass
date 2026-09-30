package dev.turtleroles.survival;

import dev.turtleroles.service.GameplayBypass;
import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Homes, a movable protected spawn, dimension access and server presentation. */
public final class SurvivalModule implements Listener, CommandExecutor, AutoCloseable {
    private final TurtleRolesPlugin plugin;
    private final File file;
    final YamlConfiguration data;
    private final Map<UUID,Object> teleporting = new HashMap<>();
    private final SurvivalSidebar sidebar;
    private final SpawnProtection protection;
    private final SpawnArea spawnArea;
    private final RandomTeleport rtp;
    private final TeleportWarmups warmups;
    private volatile boolean closed;
    public SpawnArea spawnArea(){return spawnArea;}
    boolean bypass(org.bukkit.entity.Entity player){return GameplayBypass.allowed(plugin,player);}
    private boolean allowed(Player player,World world){return bypass(player)||allowed(world);}
    private BukkitTask task;
    private final NamespacedKey streak = new NamespacedKey("conquestsmp", "kill_streak");
    public SurvivalModule(TurtleRolesPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "survival.yml");
        data = YamlConfiguration.loadConfiguration(file);
        sidebar = new SurvivalSidebar(streak, plugin::hasLoadedResourcePack);
        sidebar.shards(p -> plugin.races()==null?0:plugin.races().state(p).shards);
        protection = new SpawnProtection(this);
        spawnArea = new SpawnArea(plugin,this);
        rtp = new RandomTeleport(plugin, this::inCombat);
        warmups = new TeleportWarmups(this::teleportWait, this::inCombat);
    }
    public void start() {
        rtp.register();
        warmups.register(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(protection, plugin);
        Bukkit.getPluginManager().registerEvents(spawnArea,plugin);
        for (String command : List.of("home","homes","sethome","delhome","spawn","worldspawn","setworldspawn","dimensions","rtp"))
            Objects.requireNonNull(plugin.getCommand(command)).setExecutor(this);
        Bukkit.getWorlds().forEach(this::border);
        spawnArea.applyBorder();
        spawnArea.start();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!dev.turtleroles.service.ClientCompatibility.authenticated(player)) continue;
                if (!allowed(player,player.getWorld())) teleport(player, spawn(), false);
                sidebar.refresh(player);
            }
        }, 20, 20);
        plugin.getLogger().info("Conquest survival: rank-based home limits, selected spawn protection, dimension controls and purple sidebar enabled.");
    }
    private void border(World world) {
        if (world.getEnvironment() == World.Environment.CUSTOM) return;
        world.getWorldBorder().setCenter(0,0);
        world.getWorldBorder().setSize(Math.clamp(plugin.getConfig().getDouble("worlds.border-width",15_000),100,59_999_968));
    }
    @EventHandler public void worldLoad(WorldLoadEvent event) { border(event.getWorld()); spawnArea.applyBorder(); }
    public Location spawn() {
        Location stored = data.getLocation("spawn");
        if (stored != null && stored.getWorld() != null && stored.getWorld().getEnvironment() == World.Environment.NORMAL) return stored.clone();
        World world = Bukkit.getWorld("world_terralith");
        if (world == null) world = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NORMAL).findFirst().orElseThrow();
        return world.getSpawnLocation().clone().add(.5,0,.5);
    }
    public boolean allowed(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> data.getBoolean("dimensions.nether", true);
            case THE_END -> data.getBoolean("dimensions.end", true);
            default -> true;
        };
    }
    static int homeLimit(Role role) {
        return switch(role) { case COAL, BOOSTER -> 4; case IRON -> 6; case REDSTONE -> 8; case DIAMOND -> 13; case NETHERITE -> 23; case BOOSTER_X2 -> 7; case MEMBER -> 2; default -> role.weight()>=Role.MODERATOR.weight() ? 10 : 6; };
    }
    public static int homeLimit(Role role,int boosterTier) {
        return Math.max(homeLimit(role),boosterTier>=2?7:boosterTier==1?4:2);
    }
    private int homeLimit(Player player) {
        return homeLimit(plugin.roleService().effectiveRoleOf(player.getUniqueId()),plugin.roleService().boosterTier(player.getUniqueId()));
    }
    public static long teleportWait(Role role,int boosterTier) {
        return GameplayBypass.role(role)?0:dev.turtleroles.service.ShardRewards.interval(role,boosterTier);
    }
    private long teleportWait(Player player) {
        return teleportWait(plugin.roleService().effectiveRoleOf(player.getUniqueId()),plugin.roleService().boosterTier(player.getUniqueId()));
    }
    private void requestTeleport(Player player,Location target,boolean safety,String label) {
        if (!allowed(player,target.getWorld())) {message(player,"That dimension is currently closed.");return;}
        if (!bypass(player)&&!target.getWorld().getWorldBorder().isInside(target)) {message(player,"That destination is outside the world border.");return;}
        warmups.start(player,label,r->teleport(player,target,safety,r::active,r::finish));
    }
    private boolean admin(Player player) {
        return plugin.roleService().effectiveRoleOf(player.getUniqueId()).weight() >= Role.ADMIN.weight()
                || player.hasPermission("conquest.world.admin");
    }
    private boolean inCombat(Player player) { return !bypass(player)&&plugin.combat().tagged(player); }
    private void message(Player player, String text) { player.sendMessage(Component.text(text, NamedTextColor.LIGHT_PURPLE)); }
    @EventHandler(priority=EventPriority.NORMAL,ignoreCancelled=true)
    public void route(PlayerCommandPreprocessEvent event) {
        String[] words=event.getMessage().substring(1).trim().split("\\s+");
        String name=words[0].toLowerCase(Locale.ROOT);
        if(name.startsWith("essentials:")||name.startsWith("essentialsx:"))name=name.substring(name.indexOf(':')+1);
        if(name.equals("setspawn"))name="setworldspawn";
        if(!Set.of("home","homes","sethome","delhome","spawn","worldspawn","setworldspawn","rtp").contains(name))return;
        event.setCancelled(true);
        onCommand(event.getPlayer(),Objects.requireNonNull(plugin.getCommand(name)),name,Arrays.copyOfRange(words,1,words.length));
    }
    boolean save() {
        try {
            Path temp = file.toPath().resolveSibling(file.getName()+".tmp");
            Files.createDirectories(file.toPath().getParent());
            Files.writeString(temp,data.saveToString());
            try { Files.move(temp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch(IOException e) { plugin.getLogger().severe("Survival settings could not be saved: "+e.getMessage()); return false; }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Use this command in game."); return true; }
        if (!dev.turtleroles.service.ClientCompatibility.authenticated(player)) return true;
        if (inCombat(player) && !player.isOp()) { message(player,"Commands are disabled during combat."); return true; }
        String root = "homes."+player.getUniqueId();
        var section = data.getConfigurationSection(root);
        Set<String> homes = section == null ? Set.of() : section.getKeys(false);
        String name = args.length == 0 ? "home" : args[0].toLowerCase(Locale.ROOT);
        switch(command.getName()) {
            case "sethome", "delhome", "home" -> {
                if (!name.matches("[a-z0-9_-]{1,24}") || args.length > 1) { message(player,"Use a home name with 1-24 letters, numbers, underscores or hyphens."); return true; }
                String path = root+"."+name;
                if (command.getName().equals("home")) {
                    Location location = data.getLocation(path);
                    if (location == null || location.getWorld() == null) message(player,"That home does not exist. Use /homes.");
                    else requestTeleport(player,location,true,"Home teleport");
                } else {
                    Location previous = data.getLocation(path);
                    if (command.getName().equals("sethome")) {
                        int limit = homeLimit(player);
                        if (!bypass(player) && previous == null && homes.size() >= limit) { message(player,"Your rank can set "+limit+" homes. Use /delhome <name> first."); return true; }
                        if (!allowed(player,player.getWorld())) { message(player,"This dimension is closed."); return true; }
                        data.set(path,player.getLocation());
                    } else {
                        if (previous == null) { message(player,"That home does not exist."); return true; }
                        data.set(path,null);
                    }
                    if (!save()) { data.set(path,previous); message(player,"Could not save the change. Your previous homes are retained."); }
                    else message(player,command.getName().equals("sethome") ? "Home '"+name+"' saved." : "Home '"+name+"' removed.");
                }
            }
            case "homes" -> message(player,"Homes ("+homes.size()+"/"+(bypass(player)?"unlimited":homeLimit(player))+"): "+String.join(", ",homes)+". Use /home <name>.");
            case "spawn", "worldspawn" -> {
                if(command.getName().equals("spawn")&&args.length>0&&(name.equals("area")||name.equals("border")))spawnArea.command(player,args);
                else requestTeleport(player,spawn(),false,"Spawn teleport");
            }
            case "setworldspawn" -> {
                if (!admin(player)) { message(player,"Only administrators can set world spawn."); return true; }
                if (player.getWorld().getEnvironment() != World.Environment.NORMAL) { message(player,"Set world spawn in the overworld."); return true; }
                Location previous = data.getLocation("spawn");
                Location location = player.getLocation(); data.set("spawn",location);
                if (!save()) { data.set("spawn",previous); message(player,"Could not save spawn."); return true; }
                player.getWorld().setSpawnLocation(location);
                // Teleport/respawn point only; protection belongs to the selected cuboid.
                message(player,"World spawn saved here. Use /spawn area to select the protected no-PvP cuboid.");
            }
            case "dimensions" -> openDimensions(player);
            case "rtp" -> {
                if(!player.getWorld().getName().equals("world_terralith")||player.getWorld().getEnvironment()!=World.Environment.NORMAL)message(player,"Use /rtp in the Terralith Overworld only.");
                else warmups.start(player,"Random teleport",r->rtp.start(player,r::active,r::finish));
            }
            default -> { return false; }
        }
        return true;
    }
    private static boolean safe(Location location) {
        Block feet=location.getBlock(), head=feet.getRelative(0,1,0), floor=feet.getRelative(0,-1,0);
        return feet.isPassable() && head.isPassable() && feet.getType()!=Material.LAVA && head.getType()!=Material.LAVA
                && floor.getType()!=Material.LAVA && floor.getType()!=Material.MAGMA_BLOCK
                && floor.getType()!=Material.CAMPFIRE && floor.getType()!=Material.SOUL_CAMPFIRE
                && feet.getType()!=Material.FIRE && floor.getType().isSolid();
    }
    private void teleport(Player player, Location target, boolean checkSafety) {
        warmups.cancel(player,null);
        teleport(player,target,checkSafety,()->!closed,()->{});
    }
    private void teleport(Player player,Location target,boolean checkSafety,java.util.function.BooleanSupplier valid,Runnable done) {
        if (!allowed(player,target.getWorld())) { message(player,"That dimension is currently closed."); done.run();return; }
        if (!bypass(player) && !target.getWorld().getWorldBorder().isInside(target)) { message(player,"That destination is outside the world border.");done.run(); return; }
        Object ticket=new Object();
        teleporting.put(player.getUniqueId(),ticket);
        target.getWorld().getChunkAtAsync(target).whenComplete((chunk,error) -> {
            if(closed)return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if(!teleporting.remove(player.getUniqueId(),ticket)){done.run();return;}
                try {
                    if(!valid.getAsBoolean()||!player.isOnline()||player.isDead())return;
                    if(error!=null){message(player,"The destination could not be loaded. Please try again.");return;}
                    if (!allowed(player,target.getWorld()) || (allowed(player,player.getWorld()) && inCombat(player))) { message(player,"Teleport cancelled: combat or dimension access changed."); return; }
                    if(!bypass(player)&&!target.getWorld().getWorldBorder().isInside(target)){message(player,"That destination is outside the world border.");return;}
                    if (checkSafety && !safe(target)) { message(player,"That home is obstructed or unsafe. Clear the destination first."); return; }
                    if (!allowed(player,player.getWorld())) player.leaveVehicle();
                    player.teleport(target,PlayerTeleportEvent.TeleportCause.PLUGIN);
                } finally {done.run();}
            });
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void portal(PlayerTeleportEvent event) {
        if(event.getTo()!=null && !allowed(event.getPlayer(),event.getTo().getWorld())) {
            event.setCancelled(true); message(event.getPlayer(),"That dimension is currently closed.");
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void respawn(PlayerRespawnEvent event) {
        // Valid beds and anchors keep vanilla priority. Disabled dimensions cannot be respawned into.
        if (!allowed(event.getPlayer(),event.getRespawnLocation().getWorld()) || (!event.isBedSpawn() && !event.isAnchorSpawn())) event.setRespawnLocation(spawn());
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void firstSpawn(org.spigotmc.event.player.PlayerSpawnLocationEvent event) {
        if(!event.getPlayer().hasPlayedBefore()) {
            Location location=spawn();location.setPitch(0);event.setSpawnLocation(location);
        }
    }
    @EventHandler public void joined(PlayerJoinEvent event) {
        Player player=event.getPlayer();
        Bukkit.getScheduler().runTask(plugin,()-> { if(player.isOnline() && !allowed(player,player.getWorld())) teleport(player,spawn(),false); });
    }
    @EventHandler public void quit(PlayerQuitEvent event) { sidebar.remove(event.getPlayer()); teleporting.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void died(PlayerDeathEvent event) {
        Leaderboards.endStreak(event.getEntity());
        event.getEntity().getPersistentDataContainer().set(streak,PersistentDataType.INTEGER,0);
        Player killer=event.getEntity().getKiller();
        if(killer!=null && killer!=event.getEntity()) killer.getPersistentDataContainer().set(streak,PersistentDataType.INTEGER,
                killer.getPersistentDataContainer().getOrDefault(streak,PersistentDataType.INTEGER,0)+1);
        if(killer!=null&&killer!=event.getEntity()){Leaderboards.rememberBest(killer);Leaderboards.announceStreak(killer);}
    }
    private static final class DimensionMenu implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory(){ return inventory; }
    }
    public void openDimensions(Player player) {
        if (!admin(player) || inCombat(player)) { message(player,"This control requires an administrator outside combat."); return; }
        DimensionMenu menu=new DimensionMenu();
        menu.inventory=Bukkit.createInventory(menu,27,Component.text("Conquest Dimension Access",NamedTextColor.DARK_PURPLE));
        for(int slot : new int[]{11,15}) {
            String name=slot==11?"nether":"end";
            ItemStack item=new ItemStack(slot==11?Material.NETHERRACK:Material.END_STONE);
            var meta=item.getItemMeta();
            meta.displayName(Component.text((slot==11?"Nether":"The End")+": "+(data.getBoolean("dimensions."+name,true)?"OPEN":"CLOSED"),NamedTextColor.LIGHT_PURPLE));
            meta.lore(List.of(Component.text("Click to toggle. Closing sends everyone there to overworld spawn.")));
            item.setItemMeta(meta);menu.inventory.setItem(slot,item);
        }
        player.openInventory(menu.inventory);
    }
    @EventHandler public void menu(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof DimensionMenu))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player) || !admin(player) || inCombat(player))return;
        String dimension=event.getRawSlot()==11?"nether":event.getRawSlot()==15?"end":null;
        if(dimension==null)return;
        boolean was=data.getBoolean("dimensions."+dimension,true);data.set("dimensions."+dimension,!was);
        if(!save()){ data.set("dimensions."+dimension,was);message(player,"Could not save dimension access.");return; }
        announceDimension(dimension,!was);
        if(was) for(Player online:Bukkit.getOnlinePlayers()) if(!allowed(online,online.getWorld())) teleport(online,spawn(),false);
        openDimensions(player);
    }
    static net.kyori.adventure.title.Title dimensionTitle(String dimension,boolean opened){
        String label=dimension.equals("nether")?"The Nether":"The End";
        String heading=(dimension.equals("nether")?"NETHER":"THE END")+(opened?" OPENED":" LOCKED");
        return net.kyori.adventure.title.Title.title(
            Component.text(heading,NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD),
            Component.text(label+" has been "+(opened?"opened.":"locked."),NamedTextColor.RED),
            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(400),java.time.Duration.ofSeconds(3),java.time.Duration.ofMillis(800)));
    }
    static void announceDimensionTo(Player player,String dimension,boolean opened){
        player.showTitle(dimensionTitle(dimension,opened));player.playSound(player.getLocation(),Sound.ENTITY_ENDER_DRAGON_GROWL,.7f,1f);
    }
    private void announceDimension(String dimension,boolean opened){
        for(Player player:Bukkit.getOnlinePlayers())announceDimensionTo(player,dimension,opened);
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof DimensionMenu)event.setCancelled(true);
    }
    @Override public void close() {
        closed=true; warmups.close();
        if(task!=null)task.cancel();
        rtp.close(); spawnArea.close(); sidebar.close(); HandlerList.unregisterAll(this); HandlerList.unregisterAll(protection); HandlerList.unregisterAll(spawnArea);
    }
}


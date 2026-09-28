package dev.turtleroles.survival;

import dev.turtleroles.TurtleRolesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Camera-facing, viewer-private displays backed by the same vanilla statistics as the sidebar. */
public final class Leaderboards implements Listener, CommandExecutor, TabCompleter, AutoCloseable {
    static final NamespacedKey BEST=new NamespacedKey("conquestsmp","best_kill_streak");
    static final NamespacedKey BROKEN=new NamespacedKey("conquestsmp","best_completed_streak");
    private static final NamespacedKey CURRENT=new NamespacedKey("conquestsmp","kill_streak");
    private static final TextColor LIGHT=TextColor.color(0xE1CBFF),PURPLE=TextColor.color(0xB477FF),PINK=TextColor.color(0xEEA8ED);
    enum Metric { kills, deaths, streaks, playtime }
    record Stats(UUID id,String name,long kills,long deaths,long streaks,long playtime,long active) {
        long value(Metric metric){return switch(metric){case kills->kills;case deaths->deaths;case streaks->streaks;case playtime->playtime;};}
    }
    record StreakEntry(Stats player,long value,boolean active){}
    private record Key(UUID viewer,Metric metric){}
    private static final class View {
        final List<TextDisplay> text=new ArrayList<>();final List<ItemDisplay> heads=new ArrayList<>();
        void remove(){text.forEach(Entity::remove);heads.forEach(Entity::remove);}
        boolean valid(){return text.size()==15&&text.stream().allMatch(Entity::isValid)&&heads.stream().allMatch(Entity::isValid);}
    }
    private final TurtleRolesPlugin plugin;
    private final Path file;
    private final Map<UUID,Stats> stats=new HashMap<>();
    private final Map<UUID,ItemStack> skins=new HashMap<>();
    private final EnumMap<Metric,Location> positions=new EnumMap<>(Metric.class);
    private final Map<Key,View> views=new HashMap<>();
    private final Queue<OfflinePlayer> importQueue=new ArrayDeque<>();
    private final Set<BukkitTask> delayed=new HashSet<>();
    private BukkitTask task;private int cycles;private boolean closed;
    public Leaderboards(TurtleRolesPlugin plugin){this.plugin=plugin;file=plugin.getDataFolder().toPath().resolve("leaderboards.yml");load();}
    public void start(){
        Bukkit.getPluginManager().registerEvents(this,plugin);
        var command=Objects.requireNonNull(plugin.getCommand("leaderboard"));command.setExecutor(this);command.setTabCompleter(this);
        importQueue.addAll(Arrays.asList(Bukkit.getOfflinePlayers()));
        for(Player p:Bukkit.getOnlinePlayers()){capture(p);skin(p);}
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
    }
    public static void rememberBest(Player p){
        var data=p.getPersistentDataContainer();int current=data.getOrDefault(CURRENT,PersistentDataType.INTEGER,0);
        int best=data.getOrDefault(BEST,PersistentDataType.INTEGER,0);
        if(current>best)data.set(BEST,PersistentDataType.INTEGER,current);
    }
    public static void endStreak(Player p){
        rememberBest(p);var data=p.getPersistentDataContainer();
        int current=data.getOrDefault(CURRENT,PersistentDataType.INTEGER,0);
        if(current>data.getOrDefault(BROKEN,PersistentDataType.INTEGER,0))data.set(BROKEN,PersistentDataType.INTEGER,current);
    }
    static boolean milestone(int streak){return streak>0&&streak%10==0;}
    public static void announceStreak(Player p){
        int current=p.getPersistentDataContainer().getOrDefault(CURRENT,PersistentDataType.INTEGER,0);
        if(!milestone(current))return;
        Component message=Component.text("CONQUEST » ",PURPLE).decorate(TextDecoration.BOLD)
            .append(Component.text(p.getName()+" is on a kill streak of "+current+"!",current>=50?PINK:LIGHT));
        for(Player viewer:Bukkit.getOnlinePlayers()){viewer.sendMessage(message);viewer.playSound(viewer.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.35f,1.8f);}
    }
    private void capture(OfflinePlayer p){
        UUID id=p.getUniqueId();Stats old=stats.get(id);Player online=p.getPlayer();
        long best=old==null?0:old.streaks;long active=old==null?0:old.active;
        if(online!=null){rememberBest(online);best=Math.max(best,online.getPersistentDataContainer().getOrDefault(BROKEN,PersistentDataType.INTEGER,0));active=online.getPersistentDataContainer().getOrDefault(CURRENT,PersistentDataType.INTEGER,0);}
        String name=p.getName();if(name==null)name=old==null?id.toString().substring(0,8):old.name;
        stats.put(id,new Stats(id,name,Math.max(0,p.getStatistic(Statistic.PLAYER_KILLS)),Math.max(0,p.getStatistic(Statistic.DEATHS)),best,Integer.toUnsignedLong(p.getStatistic(Statistic.PLAY_ONE_MINUTE))/20,active));
    }
    private void skin(Player p){
        ItemStack item=new ItemStack(Material.PLAYER_HEAD);SkullMeta meta=(SkullMeta)item.getItemMeta();
        meta.setPlayerProfile(p.getPlayerProfile());item.setItemMeta(meta);skins.put(p.getUniqueId(),item);
    }
    private ItemStack head(Stats record){
        return skins.computeIfAbsent(record.id,id->{ItemStack item=new ItemStack(Material.PLAYER_HEAD);SkullMeta meta=(SkullMeta)item.getItemMeta();meta.setOwnerProfile(Bukkit.createPlayerProfile(id,record.name));item.setItemMeta(meta);return item;});
    }
    private void tick(){
        // Import only two saved players per second, never repeatedly read offline statistics.
        for(int i=0;i<2&&!importQueue.isEmpty();i++){
            OfflinePlayer p=importQueue.remove();try{if(p.hasPlayedBefore()||p.isOnline())capture(p);}catch(RuntimeException ex){plugin.getLogger().warning("Could not import leaderboard statistics for "+p.getUniqueId());}
        }
        for(Player p:Bukkit.getOnlinePlayers())capture(p);
        EnumMap<Metric,List<Stats>> rankings=new EnumMap<>(Metric.class);
        for(Metric m:Metric.values())rankings.put(m,rank(stats.values(),m));
        Set<Key> wanted=new HashSet<>();
        double distance=Math.clamp(plugin.getConfig().getDouble("leaderboards.view-distance",24),8,48);
        for(Player player:Bukkit.getOnlinePlayers()){
            if(!dev.turtleroles.service.ClientCompatibility.authenticated(player))continue;
            for(var entry:positions.entrySet()){
                Location location=entry.getValue();
                if(location.getWorld()==null||location.getWorld()!=player.getWorld()||location.distanceSquared(player.getLocation())>distance*distance||!location.isChunkLoaded())continue;
                Key key=new Key(player.getUniqueId(),entry.getKey());wanted.add(key);
                View view=views.get(key);
                if(view!=null&&!view.valid()){view.remove();views.remove(key);view=null;}
                if(view==null){view=create(player,location);views.put(key,view);}
                render(view,entry.getKey(),rankings.get(entry.getKey()),stats.get(player.getUniqueId()));
            }
        }
        views.entrySet().removeIf(e->{if(wanted.contains(e.getKey()))return false;e.getValue().remove();return true;});
        if(++cycles%60==0)save();
    }
    static List<Stats> rank(Collection<Stats> values,Metric metric){
        return values.stream().sorted(Comparator.comparingLong((Stats s)->s.value(metric)).reversed().thenComparing(s->s.name.toLowerCase(Locale.ROOT)).thenComparing(s->s.id.toString())).limit(10).toList();
    }
    static List<StreakEntry> rankStreaks(Collection<Stats> values){
        List<StreakEntry> entries=new ArrayList<>();
        for(Stats s:values){if(s.streaks>0)entries.add(new StreakEntry(s,s.streaks,false));if(s.active>0)entries.add(new StreakEntry(s,s.active,true));}
        return entries.stream().sorted(Comparator.comparingLong(StreakEntry::value).reversed().thenComparing(e->!e.active).thenComparing(e->e.player.name.toLowerCase(Locale.ROOT)).thenComparing(e->e.player.id.toString())).limit(10).toList();
    }
    static Component streakRow(int index,StreakEntry entry){
        if(entry==null)return row(index,null,Metric.streaks);
        return Component.text("#"+(index+1)+"      "+entry.player.name+"  •  "+entry.value+(entry.active?"  LIVE":"  RECORD"),TextColor.color(entry.active?0xB477FF:0x96919C));
    }
    static String value(Metric m,long value){return m==Metric.playtime?SurvivalSidebar.duration(value):Long.toString(value);}
    static Component row(int index,Stats stats,Metric m){
        Component rank=Component.text("#"+(index+1)+"      ",index<3?PINK:PURPLE);
        return rank.append(Component.text(stats==null?"Waiting for players":stats.name,LIGHT)).append(Component.text("  •  ",PURPLE)).append(Component.text(stats==null?"0":value(m,stats.value(m)),PINK));
    }
    static Component personal(Stats stats,Metric m){if(m==Metric.streaks)return Component.text("YOUR STREAK: "+(stats==null?0:stats.active)+"  •  BEST: "+(stats==null?0:Math.max(stats.active,stats.streaks)),LIGHT);return Component.text("YOUR "+(m==Metric.streaks?"BEST STREAK":m.name().toUpperCase(Locale.ROOT))+": ",LIGHT).append(Component.text(value(m,stats==null?0:stats.value(m)),PINK));}
    private void render(View view,Metric m,List<Stats> top,Stats personal){
        set(view.text.get(0),Component.text(m==Metric.streaks?"BEST KILL STREAKS":m.name().toUpperCase(Locale.ROOT),PURPLE).decorate(TextDecoration.BOLD));
        set(view.text.get(1),Component.text(switch(m){case kills->"COMPETE FOR THE MOST KILLS";case deaths->"EVERY DEATH HAS A STORY";case streaks->"YOUR GREATEST UNBROKEN RUN";case playtime->"TIME SPENT IN CONQUEST";},LIGHT));
        Component rule=Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━",TextColor.color(0x74419E));set(view.text.get(2),rule);set(view.text.get(13),rule);
        List<StreakEntry> streakTop=m==Metric.streaks?rankStreaks(stats.values()):List.of();
        for(int i=0;i<10;i++){
            StreakEntry entry=i<streakTop.size()?streakTop.get(i):null;
            Stats s=m==Metric.streaks?(entry==null?null:entry.player):(i<top.size()?top.get(i):null);
            Component line=m==Metric.streaks?streakRow(i,entry):row(i,s,m);set(view.text.get(i+3),line);
            String plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
            float x=-fontWidth(plain)*.0125f+(fontWidth("#"+(i+1)+"  ")+8)*.025f;
            view.heads.get(i).setTransformationMatrix(faceTransform(x,(11-i)*.30f+.10f));
            ItemStack desired=s==null?new ItemStack(Material.AIR):head(s);
            if(!view.heads.get(i).getItemStack().equals(desired))view.heads.get(i).setItemStack(desired);
        }
        set(view.text.get(14),personal(personal,m));
    }
    private static void set(TextDisplay display,Component text){if(!text.equals(display.text()))display.text(text);}
    private View create(Player viewer,Location anchor){
        View view=new View();
        try{
            for(int i=0;i<15;i++){
                final float y=(14-i)*.30f;
                TextDisplay display=anchor.getWorld().spawn(anchor,TextDisplay.class,e->{
                    setup(e);e.setSeeThrough(false);e.setShadowed(true);e.setDefaultBackground(false);e.setBackgroundColor(Color.fromARGB(95,15,3,25));e.setLineWidth(500);
                    e.setTransformationMatrix(new Matrix4f().translation(0,y,0));
                });view.text.add(display);viewer.showEntity(plugin,display);
            }
            for(int i=0;i<10;i++){
                final float y=(11-i)*.30f+.10f;
                ItemDisplay display=anchor.getWorld().spawn(anchor,ItemDisplay.class,e->{
                    setup(e);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
                    e.setTransformationMatrix(faceTransform(0,y));
                });view.heads.add(display);viewer.showEntity(plugin,display);
            }
            return view;
        }catch(RuntimeException e){view.remove();throw e;}
    }
    // TextDisplay's default font uses 0.025 world units per font pixel.
    // Reserve four spaces inside each row, directly between the rank and name.
    static int fontWidth(String text){
        int width=0;for(int c:text.codePoints().toArray())width+=switch(c){
            case ' '->4;case 'i','!', '.',',',':',';','|','\''->2;
            case 'l','`'->3;case 'I','t','[',']'->4;case 'f','k','(',')','{','}','<' ,'>'->5;case '@','~'->7;default->6;
        };return width;
    }
    static Matrix4f faceTransform(float x,float y){
        // Compress depth without changing the skin/profile or front-facing orientation.
        return new Matrix4f().translation(x,y,.02f).rotateY((float)Math.PI).scale(.38f,.38f,.0001f);
    }
    private static void setup(Display e){
        e.setVisibleByDefault(false);e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);
        e.setBillboard(Display.Billboard.CENTER);e.setBrightness(new Display.Brightness(15,15));e.setViewRange(1.5f);
        e.setDisplayWidth(8);e.setDisplayHeight(10);
    }
    private boolean admin(CommandSender sender){return sender.hasPermission("conquest.leaderboard.admin")||dev.turtleroles.service.GameplayBypass.allowed(plugin,sender instanceof Player p?p:null);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!admin(sender)){sender.sendMessage("Only administrators can place leaderboards.");return true;}
        Metric m=args.length==2?parse(args[1]):null;
        if(m==null||!(args[0].equalsIgnoreCase("set")||args[0].equalsIgnoreCase("remove"))){sender.sendMessage("Use /leaderboard <set|remove> <kills|deaths|streaks|playtime>.");return true;}
        Location previous=positions.get(m);
        if(args[0].equalsIgnoreCase("set")){
            if(!(sender instanceof Player p)){sender.sendMessage("Place a leaderboard in game.");return true;}
            Location l=p.getLocation().clone();l.setYaw(0);l.setPitch(0);positions.put(m,l);
        }else positions.remove(m);
        if(!save()){if(previous==null)positions.remove(m);else positions.put(m,previous);sender.sendMessage("Could not save this leaderboard. The previous placement is retained.");return true;}
        views.entrySet().removeIf(e->{if(e.getKey().metric!=m)return false;e.getValue().remove();return true;});
        sender.sendMessage(Component.text("Leaderboard "+m+" "+(positions.containsKey(m)?"placed. Its personal-stat line starts at your feet.":"removed."),LIGHT));return true;
    }
    static Metric parse(String text){try{return Metric.valueOf(text.toLowerCase(Locale.ROOT));}catch(IllegalArgumentException e){return null;}}
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
        if(!admin(sender))return List.of();List<String> options=args.length==1?List.of("set","remove"):args.length==2?Arrays.stream(Metric.values()).map(Enum::name).toList():List.of();
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return options.stream().filter(s->s.startsWith(prefix)).toList();
    }
    @EventHandler public void join(PlayerJoinEvent event){
        Player p=event.getPlayer();capture(p);skin(p);
        // Give skin providers time to apply their profile, without online profile requests every tick.
        for(long delay:new long[]{20,100}){
            BukkitTask[] holder=new BukkitTask[1];holder[0]=Bukkit.getScheduler().runTaskLater(plugin,()->{delayed.remove(holder[0]);if(!closed&&p.isOnline()){skin(p);capture(p);}},delay);delayed.add(holder[0]);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event){capture(event.getPlayer());removeViewer(event.getPlayer().getUniqueId());save();}
    @EventHandler public void changeWorld(PlayerChangedWorldEvent event){removeViewer(event.getPlayer().getUniqueId());}
    private void removeViewer(UUID id){views.entrySet().removeIf(e->{if(!e.getKey().viewer.equals(id))return false;e.getValue().remove();return true;});}
    private void load(){
        YamlConfiguration y=YamlConfiguration.loadConfiguration(file.toFile());
        for(Metric m:Metric.values()){Location l=y.getLocation("boards."+m);if(l!=null&&l.getWorld()!=null)positions.put(m,l);}
        var players=y.getConfigurationSection("players");if(players==null)return;
        for(String key:players.getKeys(false))try{
            UUID id=UUID.fromString(key);String p="players."+key+".";
            stats.put(id,new Stats(id,y.getString(p+"name",key.substring(0,8)),y.getLong(p+"kills"),y.getLong(p+"deaths"),y.getLong(p+"streaks"),y.getLong(p+"playtime"),y.getLong(p+"active")));
            ItemStack head=y.getItemStack(p+"head");if(head!=null)skins.put(id,head);
        }catch(IllegalArgumentException ex){plugin.getLogger().warning("Ignored invalid leaderboard player identifier.");}
    }
    private boolean save(){
        YamlConfiguration y=new YamlConfiguration();positions.forEach((m,l)->y.set("boards."+m,l));
        for(Stats s:stats.values()){String p="players."+s.id+".";y.set(p+"name",s.name);y.set(p+"kills",s.kills);y.set(p+"deaths",s.deaths);y.set(p+"streaks",s.streaks);y.set(p+"playtime",s.playtime);y.set(p+"active",s.active);y.set(p+"head",skins.get(s.id));}
        try{Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(tmp,y.saveToString());try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}return true;}
        catch(IOException ex){plugin.getLogger().severe("Could not save leaderboards: "+ex.getMessage());return false;}
    }
    @Override public void close(){closed=true;if(task!=null)task.cancel();delayed.forEach(BukkitTask::cancel);delayed.clear();for(Player p:Bukkit.getOnlinePlayers())capture(p);save();views.values().forEach(View::remove);views.clear();HandlerList.unregisterAll(this);}
}

package dev.turtleroles.analytics;

import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.role.Role;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Append-only, pseudonymous observations; no chat, IPs, inventory or names. */
public final class ConquestAnalytics implements Listener,CommandExecutor,AutoCloseable {
    private static ConquestAnalytics instance;
    private final TurtleRolesPlugin plugin;
    private final Path directory,journal;
    private final Map<UUID,String> sessions=new HashMap<>();
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Conquest-analytics");t.setDaemon(true);return t;});
    private final ArrayBlockingQueue<String> queue=new ArrayBlockingQueue<>(100000);
    private BukkitTask task;
    private String salt,epoch,event="";
    private boolean enabled;
    private volatile String error="";
    private long dropped;
    private int ticks;
    public ConquestAnalytics(TurtleRolesPlugin p){plugin=p;directory=p.getDataFolder().toPath().resolve("analytics");journal=directory.resolve("observations.tsv");}
    public void start() throws Exception{
        Files.createDirectories(directory);
        Path identity=directory.resolve("private-salt.txt");
        if(!Files.exists(identity))Files.writeString(identity,UUID.randomUUID().toString());
        salt=Files.readString(identity).trim();
        instance=this;
        Objects.requireNonNull(plugin.getCommand("analytics")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Path state=directory.resolve("epoch.txt");epoch=Files.exists(state)?Files.readString(state).trim():"";
        enabled=plugin.smpStarted();
        if(enabled){if(epoch.isBlank()){epoch=UUID.randomUUID().toString();Files.writeString(state,epoch);}emit("OBSERVATION_START",null,"coverage starts now; no historical backfill");}
        for(Player p:Bukkit.getOnlinePlayers())join(p,"startup");
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
    }
    public static void launch(boolean on){if(instance!=null)instance.changeLaunch(on);}
    private void changeLaunch(boolean on){
        if(on==enabled)return;
        if(!on){for(Player p:Bukkit.getOnlinePlayers())end(p,"CENSOR","launch_cancel");emit("LAUNCH_CANCEL",null,"");enabled=false;event="";return;}
        epoch=UUID.randomUUID().toString();enabled=true;
        try{Files.writeString(directory.resolve("epoch.txt"),epoch);}catch(Exception e){error=e.toString();plugin.getLogger().severe("Analytics epoch could not be saved: "+error);}
        emit("LAUNCH",null,"");for(Player p:Bukkit.getOnlinePlayers())join(p,"launch");
    }
    public static void action(Player p,String type,String detail){if(instance!=null){instance.join(p,"observed");instance.emit(type,p,detail);}}
    private boolean real(Player p){return p.isOnline()&&!p.hasMetadata("NPC")&&!plugin.getConfig().getStringList("analytics.excluded-uuids").contains(p.getUniqueId().toString());}
    private String id(UUID uuid){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((salt+uuid).getBytes(StandardCharsets.UTF_8))).substring(0,24);}catch(Exception e){throw new IllegalStateException(e);}}
    public static String clean(String value){return value==null?"":value.replace('\t',' ').replace('\n',' ').replace('\r',' ');}
    private void emit(String type,Player player,String detail){
        if(!enabled||(player!=null&&!real(player)))return;
        String staff=player==null?"":Boolean.toString(plugin.roleService().roleOf(player.getUniqueId()).weight()>=Role.HELPER.weight()||player.isOp());
        String row=System.currentTimeMillis()+"\t"+epoch+"\t"+type+"\t"+(player==null?"":id(player.getUniqueId()))+"\t"+(player==null?"":sessions.getOrDefault(player.getUniqueId(),""))+"\t"+staff+"\t"+clean(detail)+"\n";
        if(!queue.offer(row)){dropped++;if(dropped==1)plugin.getLogger().severe("Analytics queue full; observations are being dropped.");}
    }
    private void join(Player p,String reason){if(enabled&&real(p)&&!sessions.containsKey(p.getUniqueId())){sessions.put(p.getUniqueId(),UUID.randomUUID().toString());emit("JOIN",p,reason);}}
    private void end(Player p,String type,String reason){if(sessions.containsKey(p.getUniqueId())){emit(type,p,reason);sessions.remove(p.getUniqueId());}}
    private void flush(){
        List<String> rows=new ArrayList<>();queue.drainTo(rows);if(rows.isEmpty())return;
        try{Files.writeString(journal,String.join("",rows),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);error="";}
        catch(Exception ex){error=ex.toString();dropped+=rows.size();plugin.getLogger().severe("Analytics write failed: "+error);}
    }
    private boolean afk(Player p,YamlConfiguration z){Location l=p.getLocation();return z.getBoolean("enabled")&&l.getWorld().getUID().toString().equals(z.getString("world"))&&l.getX()>=z.getDouble("min-x")&&l.getX()<z.getDouble("max-x")+1&&l.getY()>=z.getDouble("min-y")&&l.getY()<z.getDouble("max-y")+1&&l.getZ()>=z.getDouble("min-z")&&l.getZ()<z.getDouble("max-z")+1;}
    private void tick(){
        if(enabled){
            String current=plugin.analyticsEvent();
            if(!current.equals(event)){if(!event.isEmpty())emit("EVENT_END",null,event);event=current;if(!event.isEmpty()){emit("EVENT_START",null,event);for(Player p:Bukkit.getOnlinePlayers())emit("EVENT_PRESENT",p,event);}}
            if(++ticks%60==0){
                var zone=YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("afk-zone.yml").toFile());
                emit("HEARTBEAT",null,"");
                for(Player p:Bukkit.getOnlinePlayers()){join(p,"observed");emit("SAMPLE",p,afk(p,zone)?"afk":"outside_afk");if(!event.isEmpty())emit("EVENT_PRESENT",p,event);}
            }
        }
        if(!queue.isEmpty())writer.execute(this::flush);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void joined(PlayerJoinEvent e){join(e.getPlayer(),"login");if(!event.isEmpty())emit("EVENT_PRESENT",e.getPlayer(),event);}
    @EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent e){end(e.getPlayer(),"QUIT","disconnect");}
    @EventHandler(priority=EventPriority.MONITOR) public void death(PlayerDeathEvent e){
        Player victim=e.getEntity(),killer=victim.getKiller();
        emit(killer!=null?"PVP_DEATH":"OTHER_DEATH",victim,"");
        if(killer!=null&&!killer.getUniqueId().equals(victim.getUniqueId()))emit("KILL",killer,"");
    }
    @Override public boolean onCommand(CommandSender s,Command c,String l,String[] a){
        if(s instanceof Player p&&plugin.roleService().roleOf(p.getUniqueId())!=Role.OWNER){s.sendMessage("Only the Owner can access analytics.");return true;}
        if(a.length==1&&a[0].equalsIgnoreCase("export")){
            s.sendMessage("Preparing private analytics export...");writer.execute(()->{flush();try{
                Path target=directory.resolve("export.tsv"),temp=directory.resolve("export.tmp");
                if(Files.exists(journal))Files.copy(journal,temp,StandardCopyOption.REPLACE_EXISTING);else Files.writeString(temp,"");
                Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
                Bukkit.getScheduler().runTask(plugin,()->s.sendMessage("Export ready: plugins/ConquestSMP/analytics/export.tsv. Dropped observations: "+dropped));
            }catch(Exception e){plugin.getLogger().severe("Analytics export failed: "+e);}});return true;
        }
        if(a.length>=2&&a[0].equalsIgnoreCase("mark")){String mark=String.join(" ",Arrays.copyOfRange(a,1,a.length));emit("MARK",null,mark);for(Player p:Bukkit.getOnlinePlayers())emit("MARK_PRESENT",p,mark);s.sendMessage(enabled?"Analytics marker recorded.":"Tracking awaits /smp start.");return true;}
        s.sendMessage("Analytics "+(enabled?"ON":"WAITING FOR /smp start")+" | sessions "+sessions.size()+" | queued "+queue.size()+" | dropped "+dropped+" | error "+error);
        s.sendMessage("/analytics export | /analytics mark <reward-or-event-label>");return true;
    }
    @Override public void close(){if(task!=null)task.cancel();for(Player p:Bukkit.getOnlinePlayers())end(p,"CENSOR","server_shutdown");emit("OBSERVATION_END",null,"");writer.execute(this::flush);writer.shutdown();try{if(!writer.awaitTermination(10,TimeUnit.SECONDS))plugin.getLogger().severe("Analytics flush still pending at shutdown");}catch(InterruptedException e){Thread.currentThread().interrupt();}HandlerList.unregisterAll(this);instance=null;}
}

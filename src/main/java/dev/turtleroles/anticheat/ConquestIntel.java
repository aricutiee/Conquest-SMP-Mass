package dev.turtleroles.anticheat;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import java.time.*;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** One staff view, with independent evidence sources and no combined guilt score. */
public final class ConquestIntel implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final IntelStore store;
    private AutoCloseable alt;
    private ClientBinding client;
    private boolean grimConnected;
    public void grimConnected(boolean ready){grimConnected=ready;}
    private BukkitTask poll;
    private volatile boolean closed;
    private final Map<UUID,String> snapshots=new HashMap<>();
    private final Map<String,Long> throttle=new ConcurrentHashMap<>();
    public ConquestIntel(JavaPlugin plugin) {
        this.plugin=plugin;
        store=new IntelStore(plugin.getDataFolder().toPath().resolve("anticheat-evidence.sqlite"),plugin.getLogger()::warning);
    }
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        connect();
        poll=plugin.getServer().getScheduler().runTaskTimer(plugin,this::scan,100,100);
    }
    private void connect() {
        var pm=plugin.getServer().getPluginManager();
        if(alt==null && pm.isPluginEnabled("AltDetector")) {
            try {alt=new AltBinding(plugin,this);plugin.getLogger().info("ConquestAC: AltDetector event bridge active; automatic alt bans blocked.");}
            catch(RuntimeException|LinkageError ex){plugin.getLogger().warning("AltDetector API mismatch: "+ex.getClass().getSimpleName());}
        }
        if(client==null && pm.isPluginEnabled("ClientPolicy")) {
            try {client=new ClientBinding(pm.getPlugin("ClientPolicy"));plugin.getLogger().info("ConquestAC: ClientPolicy inspection bridge active.");}
            catch(RuntimeException|LinkageError ex){plugin.getLogger().warning("ClientPolicy API mismatch: "+ex.getClass().getSimpleName());}
        }
    }
    @EventHandler public void enabled(PluginEnableEvent e){if(Set.of("AltDetector","ClientPolicy").contains(e.getPlugin().getName()))connect();}
    @EventHandler public void disabled(PluginDisableEvent e){
        if(e.getPlugin().getName().equals("AltDetector")){closeAlt();}
        if(e.getPlugin().getName().equals("ClientPolicy")){client=null;snapshots.clear();}
    }
    @EventHandler public void quit(PlayerQuitEvent e){snapshots.remove(e.getPlayer().getUniqueId());}
    private void scan() {
        if(client==null)return;
        try {
            for(Player player:plugin.getServer().getOnlinePlayers()) {
                var snapshot=client.snapshot(player);if(snapshot==null)continue;
                if(snapshot.detail().equals(snapshots.put(player.getUniqueId(),snapshot.detail())))continue;
                record(player.getUniqueId(),player.getName(),"CLIENT",snapshot.detail(),!snapshot.violations().isEmpty());
            }
        } catch(RuntimeException|LinkageError ex) {
            plugin.getLogger().warning("ClientPolicy inspection failed; bridge suspended: "+ex.getClass().getSimpleName());client=null;
        }
    }
    public void record(UUID id,String name,String source,String detail,boolean alert) {
        if(closed)return;
        long now=System.currentTimeMillis();
        String key=id+":"+source+":"+(source.equals("GRIM")?detail.split(";",2)[0]:"all");
        long window=source.equals("GRIM")?10_000:source.equals("ALT")?30_000:5_000;
        var accepted=new java.util.concurrent.atomic.AtomicBoolean();
        throttle.compute(key,(k,last)->{if(last==null||now-last>=window){accepted.set(true);return now;}return last;});
        if(!accepted.get())return;
        if(throttle.size()>10000)throttle.entrySet().removeIf(e->now-e.getValue()>300_000);
        store.add(new IntelStore.Entry(now,id,name,source,detail));
        if(alert) sync(()-> {
            var message=Component.text("[ConquestAC] ",TextColor.color(0xAC66E8))
                .append(Component.text(IntelStore.clean(name,32)+" | "+(source.equals("ALT")?"Possible alt account":source.equals("CLIENT")?"Suspicious mods: "+IntelVerdict.field(detail,"policy matches="):"Suspicious activity")+". ",TextColor.color(0xD7BBF5)))
                .append(Component.text("[Inspect]",TextColor.color(0xC084FC)).clickEvent(ClickEvent.runCommand("/conquestac inspect "+id)));
            for(Player staff:plugin.getServer().getOnlinePlayers())if(staff.hasPermission("conquest.anticheat.alerts"))staff.sendMessage(message);
        });
    }
    private void sync(Runnable task){if(!closed&&plugin.isEnabled())plugin.getServer().getScheduler().runTask(plugin,()->{if(!closed)task.run();});}
    public void status(CommandSender sender) {
        sender.sendMessage("ConquestAC evidence: AltDetector="+(alt!=null?"connected":"unavailable")+", ClientPolicy="+(client!=null?"connected":"unavailable")+". Local history: 30 days.");
        sender.sendMessage("Use /conquestac <player> for a short report. /conquestac details <player> shows saved evidence.");
    }
    public boolean command(CommandSender sender,String[] args) {
        if(args.length==1)args=new String[]{"inspect",args[0]};
        if(args.length!=2 || !Set.of("inspect","check","details","alts","client").contains(args[0].toLowerCase(Locale.ROOT)))return false;
        UUID id=null;String name=null;
        try{id=UUID.fromString(args[1]);}catch(IllegalArgumentException ignored){}
        if(id!=null){name=Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse(args[1]);}
        else {
            var target=Bukkit.getOfflinePlayerIfCached(args[1]);
            if(target!=null){id=target.getUniqueId();name=target.getName();}
        }
        if(id==null){sender.sendMessage("Unknown player. Use their real account name or UUID, not their nickname.");return true;}
        if(!args[0].equalsIgnoreCase("details")) {
            Player online=Bukkit.getPlayer(id);
            long sessionStart=online==null?System.currentTimeMillis()-Duration.ofHours(24).toMillis():online.getLastLogin();
            // Refresh volunteered client context on the server thread, without enforcement.
            IntelStore.Entry current=null;
            if(client!=null && online!=null)try {
                var snapshot=client.snapshot(online);
                if(snapshot!=null)current=new IntelStore.Entry(System.currentTimeMillis(),id,name,"CLIENT",snapshot.detail());
            }catch(RuntimeException|LinkageError ignored){}
            final IntelStore.Entry live=current;final UUID target=id;final String label=name;
            Path log=modLog();
            store.summary(id).thenCombine(store.mods(log,id),(entries,mods)-> {
                var copy=new ArrayList<>(entries);if(live!=null){copy.removeIf(e->e.source().equals("CLIENT"));copy.add(live);}
                return new Report(copy,mods);
            }).whenComplete((report,error)->sync(()-> {
                if(sender instanceof Player p && (!p.isOnline()||!p.hasPermission("conquest.anticheat.status")))return;
                if(error!=null){sender.sendMessage("Conquest AC: check unavailable. Please try again.");return;}
                sender.sendMessage(Component.text("Conquest AC | "+label+(online==null?" (offline)":""),TextColor.color(0xB477FF)));
                var lines=IntelVerdict.lines(report.entries(),report.mods(),System.currentTimeMillis(),sessionStart,
                    alt!=null,grimConnected,client!=null);
                for(String line:lines) {
                    var text=Component.text(line,TextColor.color(line.startsWith("Verdict")?0xB477FF:0xD7BBF5));
                    if(line.contains("[Details]"))text=text.clickEvent(ClickEvent.runCommand("/conquestac details "+target));
                    sender.sendMessage(text);
                }
            }));return true;
        }
        sender.sendMessage("Conquest AC details: "+name+" ("+id+")");
        sender.sendMessage("Latest 12 saved observations:");
        UUID target=id;
        store.recent(target).thenCombine(store.mods(modLog(),target),Report::new).whenComplete((report,error)->sync(()-> {
            if(sender instanceof Player p && (!p.isOnline()||!p.hasPermission("conquest.anticheat.status")))return;
            if(error!=null){sender.sendMessage("Evidence storage unavailable. Check server logs.");return;}
            var entries=report.entries();
            if(entries.isEmpty())sender.sendMessage("No recorded observations in the last 30 days.");
            for(var e:entries)sender.sendMessage(Component.text("["+DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(e.time()))+"] "+e.source()+": ",TextColor.color(0xAC66E8))
                .append(Component.text(e.detail(),TextColor.color(0xD7BBF5))));
            var mods=report.mods();
            if(mods.time()>0)sender.sendMessage("ModDetector last scan: "+Instant.ofEpochMilli(mods.time())+" | Suspicious signals: "+(mods.suspicious().isEmpty()?"none":String.join(", ",mods.suspicious())));
            else sender.sendMessage("ModDetector: no matching scan data available.");
        }));return true;
    }
    private Path modLog(){
        var mod=plugin.getServer().getPluginManager().getPlugin("ModDetector");Path log=null;
        if(mod!=null && mod.isEnabled()) {
            var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mod.getDataFolder(),"config.yml"));
            Path folder=mod.getDataFolder().toPath().toAbsolutePath().normalize();
            Path candidate=folder.resolve(config.getString("detections-log.file","detections.jsonl")).normalize();
            if(config.getBoolean("detections-log.enabled",true)&&candidate.startsWith(folder))log=candidate;
        }
        return log;
    }
    private record Report(List<IntelStore.Entry> entries,ModDetectorReport.Result mods) {}
    private void closeAlt(){if(alt!=null){try{alt.close();}catch(Exception ignored){}alt=null;}}
    @Override public void close(){closed=true;if(poll!=null)poll.cancel();closeAlt();client=null;HandlerList.unregisterAll(this);store.close();}
}

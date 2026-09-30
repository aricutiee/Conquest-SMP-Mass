package dev.turtleroles.survival;

import dev.turtleroles.combat.CooldownBars;
import dev.turtleroles.service.ClientCompatibility;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.function.*;

/** One cancellable request per player, including the asynchronous destination search. */
final class TeleportWarmups implements Listener, AutoCloseable {
    final class Request {
        final Player player; final Location origin; final long started; final String label;
        final Consumer<Request> action; boolean dispatched;
        Request(Player p,String label,Consumer<Request> action){
            player=p;origin=p.getLocation().clone();started=clock.getAsLong();this.label=label;this.action=action;
        }
        boolean active(){return pending.get(player.getUniqueId())==this;}
        void finish(){if(pending.remove(player.getUniqueId(),this))bars.hide(player,"teleport");}
    }
    private final Map<UUID,Request> pending=new HashMap<>();
    private final ToLongFunction<Player> duration;
    private final Predicate<Player> combat;
    private final LongSupplier clock;
    private final CooldownBars bars=new CooldownBars();
    private BukkitTask task;
    TeleportWarmups(ToLongFunction<Player> duration,Predicate<Player> combat){this(duration,combat,System::currentTimeMillis);}
    TeleportWarmups(ToLongFunction<Player> duration,Predicate<Player> combat,LongSupplier clock){this.duration=duration;this.combat=combat;this.clock=clock;}
    void register(JavaPlugin plugin){Bukkit.getPluginManager().registerEvents(this,plugin);task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);}
    void start(Player player,String label,Consumer<Request> action){
        if(pending.containsKey(player.getUniqueId())){tell(player,"A teleport is already pending.");return;}
        if(combat.test(player)){tell(player,"You cannot teleport during combat.");return;}
        Request r=new Request(player,label,action);pending.put(player.getUniqueId(),r);
        long wait=duration.applyAsLong(player);
        if(wait>0)tell(player,label+" in "+((wait+999)/1000)+" seconds. Stay still.");
        update(r);
    }
    void tick(){for(Request r:List.copyOf(pending.values()))update(r);}
    private void update(Request r){
        if(!r.active())return;
        Player p=r.player;
        if(!p.isOnline()||p.isDead()||!ClientCompatibility.authenticated(p)||combat.test(p)||moved(r.origin,p.getLocation())){
            cancel(p,"Teleport cancelled: you moved, took damage, or entered combat.");return;
        }
        long wait=Math.max(0,duration.applyAsLong(p)),elapsed=Math.max(0,clock.getAsLong()-r.started);
        if(r.dispatched){if(elapsed>wait+30_000)cancel(p,"Teleport timed out. Please try again.");return;}
        long left=wait-elapsed;
        if(left>0){bars.timer(p,"teleport",r.label,left,wait);return;}
        r.dispatched=true;bars.hide(p,"teleport");
        try{r.action.accept(r);}catch(RuntimeException e){r.finish();throw e;}
    }
    static boolean moved(Location a,Location b){return !Objects.equals(a.getWorld(),b.getWorld())||a.distanceSquared(b)>0.01;}
    void cancel(Player p,String reason){Request r=pending.get(p.getUniqueId());if(r!=null){r.finish();if(reason!=null&&p.isOnline())tell(p,reason);}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent e){
        Request r=pending.get(e.getPlayer().getUniqueId());if(r!=null&&e.getTo()!=null&&moved(r.origin,e.getTo()))cancel(e.getPlayer(),"Teleport cancelled: you moved.");
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){cancel(e.getPlayer(),null);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(EntityDamageEvent e){if(e.getEntity() instanceof Player p&&e.getFinalDamage()>0)cancel(p,"Teleport cancelled: you took damage.");}
    @EventHandler public void died(PlayerDeathEvent e){cancel(e.getEntity(),null);}
    @EventHandler public void quit(PlayerQuitEvent e){cancel(e.getPlayer(),null);}
    private static void tell(Player p,String text){p.sendMessage(Component.text(text,NamedTextColor.LIGHT_PURPLE));}
    public void close(){if(task!=null)task.cancel();pending.clear();bars.close();HandlerList.unregisterAll(this);}
}

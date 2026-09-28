package dev.turtleroles.survival;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Bounded asynchronous terrain search. All world inspection stays on the server thread. */
final class RandomTeleport implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final Predicate<Player> combat;
    private final Map<UUID, Request> pending = new HashMap<>();
    private volatile boolean closed;
    private static final class Request {
        final Player player; final World world; int attempts; BukkitTask timeout;
        Request(Player player, World world) {this.player=player;this.world=world;}
    }
    RandomTeleport(JavaPlugin plugin, Predicate<Player> combat) {
        this.plugin=plugin;this.combat=combat;
    }
    void register(){
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    void start(Player player) {
        World world=Bukkit.getWorld("world_terralith");
        if(world==null || world.getEnvironment()!=World.Environment.NORMAL){tell(player,"The Terralith Overworld is unavailable.");return;}
        if(player.getWorld()!=world){tell(player,"Use /rtp in the Terralith Overworld only.");return;}
        if(combat.test(player)){tell(player,"You cannot randomly teleport during combat.");return;}
        if(pending.containsKey(player.getUniqueId())){tell(player,"A safe location is already being found.");return;}
        if(closed)return;
        if(pending.size()>=4){tell(player,"Random teleport is busy. Try again shortly.");return;}
        Request request=new Request(player,world);pending.put(player.getUniqueId(),request);
        request.timeout=Bukkit.getScheduler().runTaskLater(plugin,()->finish(request,"Could not find a safe location in time. Please try again."),400);
        tell(player,"Finding a safe location in the Terralith Overworld..."); search(request);
    }
    private boolean active(Request r){return !closed&&pending.get(r.player.getUniqueId())==r;}
    private boolean eligible(Request r){return r.player.isOnline()&&!r.player.isDead()&&r.player.getWorld()==r.world&&!combat.test(r.player)
        &&dev.turtleroles.service.ClientCompatibility.authenticated(r.player);}
    private void search(Request r) {
        if(!active(r))return;
        if(!eligible(r)){finish(r,"Random teleport cancelled: you moved worlds, died, or entered combat.");return;}
        if(r.attempts++>=16){finish(r,"No safe dry landing spot was found. Please try again.");return;}
        WorldBorder border=r.world.getWorldBorder();Location center=border.getCenter();
        int[] xs=range(center.getX(),border.getSize()),zs=range(center.getZ(),border.getSize());
        if(xs==null||zs==null){finish(r,"The current world border is too small for a safe random teleport.");return;}
        int x=ThreadLocalRandom.current().nextInt(xs[0],xs[1]+1),z=ThreadLocalRandom.current().nextInt(zs[0],zs[1]+1);
        r.world.getChunkAtAsync(x>>4,z>>4,true).whenComplete((chunk,error)->{
            if(closed)return;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(!active(r))return;
                if(!eligible(r)){finish(r,"Random teleport cancelled: you moved worlds, died, or entered combat.");return;}
                if(error!=null||chunk==null){finish(r,"The destination could not be loaded. Please try again.");return;}
                int y=r.world.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES)+1;
                Location to=new Location(r.world,x+.5,y,z+.5,r.player.getYaw(),0);
                if(!inside(to,r.world.getWorldBorder())||!safe(to)){search(r);return;}
                // Synchronous teleport after chunk completion keeps eligibility and safety atomic.
                boolean moved=r.player.teleport(to,PlayerTeleportEvent.TeleportCause.COMMAND);
                finish(r,moved?"Teleported to a random location in the Terralith Overworld.":"Random teleport was cancelled by the server.");
            });
        });
    }
    static int[] range(double center,double width){
        int low=(int)Math.ceil(Math.max(-29_999_983,center-width/2+1)-.5);
        int high=(int)Math.floor(Math.min(29_999_983,center+width/2-1)-.5);
        return low<=high?new int[]{low,high}:null;
    }
    static boolean inside(Location location,WorldBorder border){
        Location center=border.getCenter();double radius=border.getSize()/2-1;
        return radius>=0&&Math.abs(location.getX()-center.getX())<=radius&&Math.abs(location.getZ()-center.getZ())<=radius;
    }
    static boolean safe(Location l){
        World w=l.getWorld();if(l.getBlockY()<=w.getMinHeight()||l.getBlockY()+1>=w.getMaxHeight())return false;
        Block floor=l.getBlock().getRelative(0,-1,0),feet=l.getBlock(),head=feet.getRelative(0,1,0);
        return floor.getType().isOccluding()&&!hazard(floor.getType())&&clear(feet)&&clear(head);
    }
    private static boolean clear(Block b){return b.isPassable()&&!b.isLiquid()&&!hazard(b.getType());}
    static boolean hazard(Material m){return switch(m){
        case LAVA,WATER,MAGMA_BLOCK,CACTUS,CAMPFIRE,SOUL_CAMPFIRE,FIRE,SOUL_FIRE,POWDER_SNOW,SWEET_BERRY_BUSH,WITHER_ROSE,POINTED_DRIPSTONE,COBWEB,NETHER_PORTAL,END_PORTAL -> true;
        default -> false;
    };}
    private static void tell(Player p,String text){p.sendMessage(Component.text(text,NamedTextColor.LIGHT_PURPLE));}
    private void finish(Request r,String message){
        if(pending.remove(r.player.getUniqueId(),r)){if(r.timeout!=null)r.timeout.cancel();if(message!=null&&r.player.isOnline())tell(r.player,message);}
    }
    @EventHandler public void quit(PlayerQuitEvent e){Request r=pending.get(e.getPlayer().getUniqueId());if(r!=null)finish(r,null);}
    @Override public void close(){closed=true;for(Request r:List.copyOf(pending.values()))finish(r,null);HandlerList.unregisterAll(this);}
}


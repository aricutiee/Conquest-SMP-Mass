package dev.turtleroles.survival;

import dev.turtleroles.TurtleRolesPlugin;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;

/** Private, bounded client glass previews. The world is never edited. */
final class SpawnBarrier implements AutoCloseable {
    record Cell(UUID world,int x,int y,int z) {
        Location location(World world){return new Location(world,x,y,z);}
    }
    private final TurtleRolesPlugin plugin;
    private final SurvivalModule module;
    private final Map<UUID,Set<Cell>> shown=new HashMap<>();
    private final Map<UUID,Long> bounced=new HashMap<>();
    private final Map<UUID,BukkitTask> pending=new HashMap<>();
    private BukkitTask ticker;
    private int ticks;
    SpawnBarrier(TurtleRolesPlugin plugin,SurvivalModule module){this.plugin=plugin;this.module=module;}
    void start(){ticker=Bukkit.getScheduler().runTaskTimer(plugin,()->{
        ticks++;
        for(Player player:Bukkit.getOnlinePlayers()){
            refresh(player,ticks%4==0);
            SpawnRegion region=module.spawnArea().region();
            if(eligible(player)&&region!=null&&!region.contains(player.getLocation())&&distance(region,player.getLocation())<=0.34){
                Vector outward=outward(region,player.getLocation());
                if(outward!=null)bounce(player,outward);
            }
        }
    },1,5);}
    boolean eligible(Player player){return !player.isDead()&&!module.bypass(player)&&plugin.combat().tagged(player);}
    static double distance(SpawnRegion r,Location l){
        if(l.getWorld()==null||!r.world().equals(l.getWorld().getUID()))return Double.POSITIVE_INFINITY;
        double dx=Math.max(Math.max(r.minX()-l.getX(),l.getX()-(r.maxX()+1.0)),0);
        double dz=Math.max(Math.max(r.minZ()-l.getZ(),l.getZ()-(r.maxZ()+1.0)),0);
        return Math.hypot(dx,dz);
    }
    static Vector outward(SpawnRegion r,Location l){
        if(r.contains(l)||!r.world().equals(l.getWorld().getUID()))return null;
        double x=l.getX()-Math.clamp(l.getX(),r.minX(),r.maxX()+1.0);
        double z=l.getZ()-Math.clamp(l.getZ(),r.minZ(),r.maxZ()+1.0);
        // The positive edge itself is outside the half-open region.
        if(x==0&&z==0){if(l.getX()>=r.maxX()+1.0)x=1;else if(l.getZ()>=r.maxZ()+1.0)z=1;else return null;}
        return new Vector(x,0,z).normalize();
    }
    /** Swept horizontal player footprint catches fast movement through the entire region. */
    static boolean crosses(SpawnRegion r,Location from,Location to){
        if(to==null||from.getWorld()==null||!from.getWorld().equals(to.getWorld())||!r.world().equals(from.getWorld().getUID())||r.contains(from))return false;
        double dx=to.getX()-from.getX(),dz=to.getZ()-from.getZ();
        Vector normal=outward(r,from);
        if(normal==null||dx*normal.getX()+dz*normal.getZ()>=-0.000001)return false;
        double enter=0,exit=1;
        double[] pos={from.getX(),from.getZ()},delta={dx,dz},low={r.minX()-.32,r.minZ()-.32},high={r.maxX()+1.32,r.maxZ()+1.32};
        for(int axis=0;axis<2;axis++){
            if(Math.abs(delta[axis])<1e-9){if(pos[axis]<low[axis]||pos[axis]>high[axis])return false;continue;}
            double a=(low[axis]-pos[axis])/delta[axis],b=(high[axis]-pos[axis])/delta[axis];
            enter=Math.max(enter,Math.min(a,b));exit=Math.min(exit,Math.max(a,b));
            if(enter>exit)return false;
        }
        return exit>=0&&enter<=1;
    }
    static Set<Cell> cells(SpawnRegion r,Location l,int radius){
        if(r==null||r.contains(l)||distance(r,l)>radius)return Set.of();
        World w=l.getWorld();Set<Cell> result=new HashSet<>();
        int x=l.getBlockX(),z=l.getBlockZ();
        // Real block packets are valid only within the world's build range. Entry checks have no Y limit.
        int bottom=Math.max(w.getMinHeight(),l.getBlockY()-2),top=Math.min(w.getMaxHeight()-1,l.getBlockY()+4);
        for(int xx=Math.max(r.minX(),x-radius);xx<=Math.min(r.maxX(),x+radius);xx++)
            for(int zz=Math.max(r.minZ(),z-radius);zz<=Math.min(r.maxZ(),z+radius);zz++){
                if(xx!=r.minX()&&xx!=r.maxX()&&zz!=r.minZ()&&zz!=r.maxZ())continue;
                if(!w.isChunkLoaded(xx>>4,zz>>4))continue;
                for(int y=bottom;y<=top;y++)if(w.getBlockAt(xx,y,zz).getType().isAir())result.add(new Cell(r.world(),xx,y,zz));
            }
        return result;
    }
    void refresh(Player player,boolean resend){
        Set<Cell> next=eligible(player)?cells(module.spawnArea().region(),player.getLocation(),6):Set.of();
        Set<Cell> previous=shown.getOrDefault(player.getUniqueId(),Set.of());
        for(Cell cell:previous)if(!next.contains(cell))restore(player,cell);
        BlockData glass=Material.RED_STAINED_GLASS.createBlockData();
        for(Cell cell:next)if(resend||!previous.contains(cell))player.sendBlockChange(cell.location(player.getWorld()),glass);
        if(next.isEmpty())shown.remove(player.getUniqueId());else shown.put(player.getUniqueId(),next);
    }
    private void restore(Player player,Cell cell){
        World world=player.getWorld();
        if(world.getUID().equals(cell.world())&&world.isChunkLoaded(cell.x()>>4,cell.z()>>4)&&cell.y()>=world.getMinHeight()&&cell.y()<world.getMaxHeight())
            player.sendBlockChange(cell.location(world),world.getBlockAt(cell.x(),cell.y(),cell.z()).getBlockData());
    }
    static boolean clear(Location location){
        World world=location.getWorld();
        // Above/below build limits there can be no terrain to collide with.
        for(int x=(int)Math.floor(location.getX()-.3);x<=(int)Math.floor(location.getX()+.3);x++)
            for(int z=(int)Math.floor(location.getZ()-.3);z<=(int)Math.floor(location.getZ()+.3);z++){
                if(!world.isChunkLoaded(x>>4,z>>4))return false;
                for(int y=location.getBlockY();y<=Math.floor(location.getY()+1.8);y++)
                    if(y>=world.getMinHeight()&&y<world.getMaxHeight()&&!world.getBlockAt(x,y,z).isPassable())return false;
            }
        return true;
    }
    static Location retreat(SpawnRegion r,Location from,Vector normal){
        // Prefer the last legal position; only create room if already touching the boundary.
        if(distance(r,from)>.65&&clear(from))return from.clone();
        for(double step:new double[]{.65,1,1.5,2,3}){
            Location candidate=from.clone().add(normal.clone().multiply(step));
            if(distance(r,candidate)>.35&&clear(candidate))return candidate;
        }
        return from.clone(); // Never invent an unchecked position inside a wall.
    }
    void bounce(Player player,Vector outward){
        long now=System.currentTimeMillis();UUID id=player.getUniqueId();
        if(now-bounced.getOrDefault(id,0L)<600||pending.containsKey(id))return;
        bounced.put(id,now);
        Location anchor=player.getLocation().clone();
        // Apply after the movement correction, not in the middle of its position packet.
        pending.put(id,Bukkit.getScheduler().runTask(plugin,()->{
            pending.remove(id);
            SpawnRegion region=module.spawnArea().region();
            if(!player.isOnline()||!eligible(player)||region==null||region.contains(player.getLocation())||!anchor.getWorld().equals(player.getWorld())||anchor.distanceSquared(player.getLocation())>16)return;
            if(player.isGliding())player.setGliding(false);
            Vector velocity=player.getVelocity();
            // Cancel inward momentum so a fast wind charge cannot overpower the outward impulse.
            double inward=velocity.getX()*outward.getX()+velocity.getZ()*outward.getZ();
            if(inward<0)player.setVelocity(velocity.subtract(outward.clone().multiply(inward)));
            double strength=plugin.getConfig()==null?1:Math.clamp(plugin.getConfig().getDouble("spawn-barrier.knockback-strength",1),.1,3);
            player.knockback(strength,-outward.getX(),-outward.getZ());
            player.playSound(player.getLocation(),Sound.BLOCK_GLASS_HIT,.5f,.8f);
        }));
    }
    void forget(Player player){
        for(Cell cell:shown.getOrDefault(player.getUniqueId(),Set.of()))restore(player,cell);
        shown.remove(player.getUniqueId());bounced.remove(player.getUniqueId());
        BukkitTask task=pending.remove(player.getUniqueId());if(task!=null)task.cancel();
    }
    @Override public void close(){
        if(ticker!=null)ticker.cancel();
        for(Player player:Bukkit.getOnlinePlayers())forget(player);
        pending.values().forEach(BukkitTask::cancel);pending.clear();shown.clear();bounced.clear();
    }
}

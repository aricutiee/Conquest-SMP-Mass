package dev.turtleroles.survival;
import org.bukkit.Location;
import java.util.UUID;
/** Inclusive horizontal selected blocks at every altitude. Saved corner Y values are ignored. */
public record SpawnRegion(UUID world,int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
    public static SpawnRegion between(Location a,Location b){
        if(!a.getWorld().equals(b.getWorld()))throw new IllegalArgumentException("Corners must be in the same world.");
        return new SpawnRegion(a.getWorld().getUID(),Math.min(a.getBlockX(),b.getBlockX()),Math.min(a.getBlockY(),b.getBlockY()),Math.min(a.getBlockZ(),b.getBlockZ()),Math.max(a.getBlockX(),b.getBlockX()),Math.max(a.getBlockY(),b.getBlockY()),Math.max(a.getBlockZ(),b.getBlockZ()));
    }
    public boolean contains(Location l){return l!=null&&l.getWorld()!=null&&world.equals(l.getWorld().getUID())&&l.getX()>=minX&&l.getX()<maxX+1.0&&l.getZ()>=minZ&&l.getZ()<maxZ+1.0;}
    public double centerX(){return (minX+maxX+1.0)/2;}
    public double centerZ(){return (minZ+maxZ+1.0)/2;}
    public double borderSize(){return Math.max(maxX-minX+1.0,maxZ-minZ+1.0);}
}

package dev.turtleroles.events;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Matrix4f;

/** A non-pickup visual; exactly one genuine reward is released after seven seconds. */
final class MaceRise implements AutoCloseable {
    private final JavaPlugin plugin;private final Location origin;private final ItemStack item;private final Runnable release;
    private final boolean preview;
    private ItemDisplay display;private BukkitTask task;private int age;private boolean finished;private Chunk chunk;
    MaceRise(JavaPlugin plugin,Location origin,ItemStack item,Runnable release){this(plugin,origin,item,release,false);}
    MaceRise(JavaPlugin plugin,Location origin,ItemStack item,Runnable release,boolean preview){this.plugin=plugin;this.origin=origin.clone();this.item=item.clone();this.release=release;this.preview=preview;}
    static double height(int tick){return 1+5*Math.min(100,Math.max(0,tick))/100.0;}
    static boolean shaking(int tick){return tick>=100&&tick<140;}
    void start(){
        chunk=origin.getChunk();chunk.addPluginChunkTicket(plugin);
        display=origin.getWorld().spawn(origin.clone().add(0,1,0),ItemDisplay.class,e->{e.setItemStack(item);e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setBrightness(new Display.Brightness(15,15));e.setTransformationMatrix(new Matrix4f().scale(3));e.setTeleportDuration(1);});
        origin.getWorld().playSound(origin,Sound.BLOCK_BEACON_ACTIVATE,.7f,.6f);
        task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            ++age;
            if(preview&&age>=140&&age<180){
                // Visual-only fall and landing; never create a collectible reward.
                double progress=Math.min(1,(age-140)/15.0);
                display.teleport(origin.clone().add(0,6-5*progress*progress,0));
                display.setTransformationMatrix(new Matrix4f().scale((float)(3-2*progress)));
                if(age==155)origin.getWorld().playSound(origin,Sound.BLOCK_HEAVY_CORE_PLACE,.8f,.7f);
                return;
            }
            if(age>=(preview?180:140)){finish();return;}
            boolean shake=shaking(age);double x=shake?Math.sin(age*2.3)*.22:0,z=shake?Math.cos(age*2.8)*.22:0;
            Location at=origin.clone().add(x,height(age),z);display.teleport(at);
            display.setTransformationMatrix(new Matrix4f().rotateY((float)(age*(shake?.55:.045))).rotateZ((float)(shake?Math.sin(age*1.7)*.3:0)).scale(3));
            if(age%5==0)origin.getWorld().spawnParticle(Particle.END_ROD,at,2,.15,.15,.15,.01);
        },1,1);
    }
    private void finish(){if(finished)return;finished=true;if(task!=null)task.cancel();if(display!=null)display.remove();
        try{origin.getWorld().playSound(origin,Sound.BLOCK_HEAVY_CORE_PLACE,.8f,.7f);release.run();}
        finally{if(chunk!=null)chunk.removePluginChunkTicket(plugin);}
    }
    @Override public void close(){finish();}
}

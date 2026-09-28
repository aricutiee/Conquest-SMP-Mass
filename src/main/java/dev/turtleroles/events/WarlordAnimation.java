package dev.turtleroles.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.time.Duration;
import java.util.*;

/** Display replicas never contain collectible rewards. Real prizes are saved by WarlordHunt. */
final class WarlordAnimation implements AutoCloseable {
    private static final int RISE_TICKS=160, RELEASE_TICK=180, END_TICK=260;
    private final JavaPlugin plugin;
    private final Set<Sequence> animations=new HashSet<>();
    private final Map<UUID,Integer> musicUsers=new HashMap<>();
    private final Map<UUID,String> musicSounds=new HashMap<>();
    WarlordAnimation(JavaPlugin plugin){this.plugin=plugin;}

    void play(Location origin,List<ItemStack> gear,boolean preview,Runnable finished) {
        if(origin.getWorld()==null)return;
        Sequence sequence=new Sequence(origin,preview,finished);
        animations.add(sequence);
        try {sequence.start(gear);sequence.runTaskTimer(plugin,0,2);}
        catch(RuntimeException exception){sequence.cleanup();throw exception;}
    }

    static boolean visibleGear(ItemStack item){
        return item!=null&&!item.getType().isAir();
    }

    private final class Sequence extends BukkitRunnable {
        final Location base;
        final World world;
        final boolean preview;
        final Runnable finished;
        final List<ItemDisplay> group=new ArrayList<>();
        final List<Location> previous=new ArrayList<>();
        final Set<UUID> audience=new HashSet<>();
        final double height;
        int age;
        boolean cleaned;
        Sequence(Location origin,boolean preview,Runnable finished){
            base=origin.clone();world=base.getWorld();this.preview=preview;this.finished=finished;
            height=Math.max(2,Math.min(28,world.getMaxHeight()-8-base.getY()));
        }
        void start(List<ItemStack> gear){
            for(ItemStack item:gear){
                if(!visibleGear(item))continue;
                ItemDisplay display=world.spawn(base.clone().add(0,1,0),ItemDisplay.class,d->{
                    d.setItemStack(item.clone());d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setTeleportDuration(2);
                    d.setBrightness(new Display.Brightness(15,15));d.setViewRange(2f);
                });
                group.add(display);previous.add(display.getLocation());
            }
            for(Player player:world.getPlayers())if(player.getLocation().distanceSquared(base)<=128*128){
                audience.add(player.getUniqueId());
                if(musicUsers.merge(player.getUniqueId(),1,Integer::sum)==1){
                    String sound=dev.turtleroles.service.ClientCompatibility.bedrock(player)?"minecraft:music_disc.11":"conquestsmp:warlord_dread";
                    musicSounds.put(player.getUniqueId(),sound);
                    player.playSound(player.getLocation(),sound,SoundCategory.RECORDS,.65f,1f);
                }
            }
        }
        @Override public void run(){
            try {advance();}
            catch(RuntimeException exception){cancel();cleanup();plugin.getLogger().log(java.util.logging.Level.WARNING,"Warlord animation interrupted; continuing saved hunt",exception);finished.run();}
        }
        void advance(){
            age+=2;
            if(age<=RELEASE_TICK){
                double progress=Math.min(1,age/(double)RISE_TICKS);
                double eased=progress*progress*(3-2*progress);
                for(int i=0;i<group.size();i++){
                    double angle=angle(i);
                    Location point=base.clone().add(Math.cos(angle)*2.6,1+(height-1)*eased,Math.sin(angle)*2.6);
                    point.setYaw((float)(-angle*180/Math.PI));
                    move(i,point);previous.set(i,point);
                }
            }
            if(age==RELEASE_TICK){
                for(UUID id:audience){Player player=Bukkit.getPlayer(id);if(player!=null&&player.getWorld().equals(world))
                    player.playSound(player.getLocation(),Sound.ENTITY_GENERIC_EXPLODE,SoundCategory.PLAYERS,.35f,1.65f);}
            }
            if(age>=RELEASE_TICK&&age<=RELEASE_TICK+24){
                double radius=2.6+(age-RELEASE_TICK)*.23;
                for(int i=0;i<40;i++){
                    double angle=i*Math.PI*2/40;
                    Location point=base.clone().add(Math.cos(angle)*radius,height+(age-RELEASE_TICK)*.09,Math.sin(angle)*radius);
                    if(loaded(point))world.spawnParticle(Particle.CLOUD,point,3,.25,.3,.25,.025);
                }
            }
            if(age>RELEASE_TICK){
                double progress=(age-RELEASE_TICK)/(double)(END_TICK-RELEASE_TICK);
                double distance=2.6+72*progress*progress;
                for(int i=0;i<group.size();i++){
                    double angle=angle(i);
                    Location point=base.clone().add(Math.cos(angle)*distance,height+Math.sin(progress*Math.PI)*9+progress*3,Math.sin(angle)*distance);
                    point.setYaw((float)(-angle*180/Math.PI));point.setPitch((float)(progress*300));
                    move(i,point);
                    Location last=previous.get(i);
                    for(int j=1;j<=6;j++){
                        double part=j/6.0;
                        Location trail=last.clone().add((point.getX()-last.getX())*part,(point.getY()-last.getY())*part,(point.getZ()-last.getZ())*part);
                        if(!loaded(trail))continue;
                        world.spawnParticle(Particle.END_ROD,trail,2,.025,.025,.025,0);
                        if(j%2==0)world.spawnParticle(Particle.CLOUD,trail,1,.1,.1,.1,.004);
                    }
                    previous.set(i,point);
                }
            }
            if(age>=END_TICK){
                cancel();cleanup();
                Title title=Title.title(Component.text("THE HUNT BEGINS",TextColor.color(0x681BB0)).decorate(TextDecoration.BOLD),
                    Component.text(preview?"Animation preview":"Seven relics scattered across the world",TextColor.color(0xDEC8FF)),
                    Title.Times.times(Duration.ofMillis(300),Duration.ofSeconds(4),Duration.ofMillis(900)));
                for(Player player:Bukkit.getOnlinePlayers())if(!preview||(audience.contains(player.getUniqueId())&&player.getWorld().equals(world)))player.showTitle(title);
                finished.run();
            }
        }
        double angle(int index){return 2*Math.PI*index/group.size()+Math.min(age,RISE_TICKS)*.022;}
        boolean loaded(Location location){return world.isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4);}
        void move(int index,Location point){
            ItemDisplay display=group.get(index);
            if(!display.isValid())return;
            if(loaded(point))display.teleport(point);else display.remove();
        }
        void cleanup(){
            if(cleaned)return;cleaned=true;
            group.forEach(ItemDisplay::remove);animations.remove(this);
            for(UUID id:audience){
                int users=musicUsers.getOrDefault(id,1)-1;
                if(users>0)musicUsers.put(id,users);
                else {musicUsers.remove(id);Player player=Bukkit.getPlayer(id);String sound=musicSounds.remove(id);if(player!=null&&sound!=null)player.stopSound(sound,SoundCategory.RECORDS);}
            }
        }
    }
    @Override public void close(){for(Sequence sequence:List.copyOf(animations)){sequence.cancel();sequence.cleanup();}}
}

package dev.turtleroles.survival;

import dev.turtleroles.service.ConquestMotd;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Persistent dragon health contribution and encounter damage rules. */
public final class ConquestDragon implements Listener,AutoCloseable {
    static final NamespacedKey HEALTH=new NamespacedKey("conquestsmp","dragon_health_bonus");
    private final JavaPlugin plugin;
    private final Map<UUID,EnderDragon> loaded=new HashMap<>();
    private BukkitTask task;
    public ConquestDragon(JavaPlugin plugin){this.plugin=plugin;}
    public void start(){
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Bukkit.getWorlds().forEach(w->w.getEntitiesByClass(EnderDragon.class).forEach(this::track));
        task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            loaded.values().removeIf(d->!d.isValid()||d.isDead());
            loaded.values().forEach(this::apply);
        },1,20);
    }
    private void track(EnderDragon dragon){loaded.put(dragon.getUniqueId(),dragon);apply(dragon);}
    static Component name(){
        return MiniMessage.miniMessage().deserialize("<bold><gradient:#08000D:#8C205C:#A347E0>"+ConquestMotd.smallCaps("ender dragon")+"</gradient></bold>");
    }
    void apply(EnderDragon dragon){
        if(dragon.isDead()||dragon.getHealth()<=0)return;
        AttributeInstance max=dragon.getAttribute(Attribute.MAX_HEALTH);if(max==null)return;
        double multiplier=Math.clamp(plugin.getConfig().getDouble("ender-dragon.health-multiplier",5),1,5);
        double bonus=200*(multiplier-1);
        AttributeModifier own=max.getModifiers().stream().filter(m->m.getKey().equals(HEALTH)).findFirst().orElse(null);
        if(own==null&&bonus>0||own!=null&&own.getAmount()!=bonus){
            double fraction=Math.clamp(dragon.getHealth()/max.getValue(),0,1);
            if(own!=null)max.removeModifier(own);
            if(bonus>0)max.addModifier(new AttributeModifier(HEALTH,bonus,AttributeModifier.Operation.ADD_NUMBER));
            // First adoption preserves damage already suffered, rather than fully healing an active fight.
            dragon.setHealth(Math.min(max.getValue(),fraction*max.getValue()));
        }
        Component title=name();if(!title.equals(dragon.customName()))dragon.customName(title);
        var bar=dragon.getBossBar();
        if(bar!=null){bar.setColor(BarColor.PURPLE);bar.setProgress(Math.clamp(dragon.getHealth()/max.getValue(),0,1));}
    }
    static EnderDragon dragon(Entity entity){
        if(entity instanceof EnderDragon d)return d;
        if(entity instanceof EnderDragonPart part)return part.getParent();
        return null;
    }
    static boolean perched(EnderDragon.Phase phase){
        return phase==EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET||phase==EnderDragon.Phase.ROAR_BEFORE_ATTACK||phase==EnderDragon.Phase.BREATH_ATTACK;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent event){
        EnderDragon dragon=dragon(event.getEntity());if(dragon==null)return;
        var cause=event.getCause();
        if(cause==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION||cause==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                ||event instanceof EntityDamageByEntityEvent hit&&hit.getDamager() instanceof Firework){event.setCancelled(true);return;}
        if(!perched(dragon.getPhase()))return;
        // Administrative kill and world cleanup remain possible.
        if(cause==EntityDamageEvent.DamageCause.KILL||cause==EntityDamageEvent.DamageCause.VOID)return;
        boolean melee=event instanceof EntityDamageByEntityEvent hit&&hit.getDamager() instanceof Player
            &&(cause==EntityDamageEvent.DamageCause.ENTITY_ATTACK||cause==EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK);
        if(!melee)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void spawn(EntitySpawnEvent event){if(event.getEntity() instanceof EnderDragon d)track(d);}
    @EventHandler public void load(EntitiesLoadEvent event){for(Entity entity:event.getEntities())if(entity instanceof EnderDragon d)track(d);}
    @EventHandler public void unload(EntitiesUnloadEvent event){for(Entity entity:event.getEntities())if(entity instanceof EnderDragon d)loaded.remove(d.getUniqueId());}
    @EventHandler public void world(WorldLoadEvent event){event.getWorld().getEntitiesByClass(EnderDragon.class).forEach(this::track);}
    @EventHandler public void death(EntityDeathEvent event){if(event.getEntity() instanceof EnderDragon d)loaded.remove(d.getUniqueId());}
    public void close(){if(task!=null)task.cancel();loaded.clear();HandlerList.unregisterAll(this);}
}

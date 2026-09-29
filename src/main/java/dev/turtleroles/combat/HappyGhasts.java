package dev.turtleroles.combat;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** Stable keyed modifiers prevent repeated multiplication after chunk loads/restarts. */
public final class HappyGhasts implements Listener {
    private static final NamespacedKey HEALTH=new NamespacedKey("conquestsmp","happy_ghast_health");
    private static final NamespacedKey SPEED=new NamespacedKey("conquestsmp","happy_ghast_speed");
    private final ConquestCombat combat;
    public HappyGhasts(JavaPlugin plugin,ConquestCombat combat){
        this.combat=combat;
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        for(var world:plugin.getServer().getWorlds())for(var ghast:world.getEntitiesByClass(HappyGhast.class))apply(ghast);
    }
    static void apply(HappyGhast ghast) {
        var health=ghast.getAttribute(Attribute.MAX_HEALTH);var speed=ghast.getAttribute(Attribute.FLYING_SPEED);
        if(health!=null && health.getModifier(HEALTH)==null) {
            double fraction=ghast.getHealth()/Math.max(1,health.getValue());
            health.addModifier(new AttributeModifier(HEALTH,4,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            if(!ghast.isDead())ghast.setHealth(Math.max(.01,Math.min(health.getValue(),fraction*health.getValue())));
        }
        if(speed!=null && speed.getModifier(SPEED)==null)
            speed.addModifier(new AttributeModifier(SPEED,2,AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void spawn(CreatureSpawnEvent event){if(event.getEntity() instanceof HappyGhast ghast)apply(ghast);}
    @EventHandler public void loaded(EntitiesLoadEvent event){for(Entity entity:event.getEntities())if(entity instanceof HappyGhast ghast)apply(ghast);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void mount(EntityMountEvent event) {
        if(event.getEntity() instanceof Player player && event.getMount() instanceof HappyGhast && combat.tagged(player)) {
            event.setCancelled(true);player.sendActionBar(Component.text("Happy Ghasts cannot be ridden during combat.",NamedTextColor.RED));
        }
    }
    static void dismount(Player player){if(player.getVehicle() instanceof HappyGhast)player.leaveVehicle();}
}

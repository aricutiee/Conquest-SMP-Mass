package dev.turtleroles.combat;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Limits health damage, while leaving the normal armor/Resistance/absorption pipeline intact. */
public final class WeaponDamageCaps implements Listener {
    private final JavaPlugin plugin;
    public WeaponDamageCaps(JavaPlugin plugin){this.plugin=plugin;}
    static double fraction(Material weapon){return weapon==Material.MACE?.99:weapon.name().endsWith("_SPEAR")?.70:0;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent event){
        if(event.isCancelled()||!(event.getEntity() instanceof Player target)||!(event.getDamager() instanceof Player attacker))return;
        if(event.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK&&event.getCause()!=EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)return;
        Material weapon=attacker.getInventory().getItemInMainHand().getType();double ratio=fraction(weapon);if(ratio==0)return;
        ratio=Math.clamp(plugin.getConfig().getDouble("weapon-damage-caps."+(weapon==Material.MACE?"mace":"spear"),ratio),.01,1);
        var maximum=target.getAttribute(Attribute.MAX_HEALTH);if(maximum==null)return;
        cap(event,maximum.getValue()*ratio);
    }
    static void cap(EntityDamageEvent event,double limit){
        double original=event.getDamage();if(event.getFinalDamage()<=limit||original<=0||!Double.isFinite(original))return;
        // setDamage recalculates vanilla modifiers. Search conservatively because armor is nonlinear.
        double low=0,high=original;
        for(int i=0;i<48;i++){
            double middle=(low+high)/2;event.setDamage(middle);
            if(event.getFinalDamage()>limit)high=middle;else low=middle;
        }
        event.setDamage(low);
    }
}

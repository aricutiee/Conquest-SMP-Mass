package dev.turtleroles.combat;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WeaponDamageCapsTest {
    @Test void capUsesMaximumHealthAndDoesNotScaleWithRemainingHealth(){
        for(Material weapon:new Material[]{Material.NETHERITE_SPEAR,Material.MACE}){
            var plugin=mock(JavaPlugin.class);when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            Player target=mock(Player.class),attacker=mock(Player.class);var inventory=mock(PlayerInventory.class);when(attacker.getInventory()).thenReturn(inventory);when(inventory.getItemInMainHand()).thenReturn(new ItemStack(weapon));
            AttributeInstance maximum=mock(AttributeInstance.class);when(target.getAttribute(Attribute.MAX_HEALTH)).thenReturn(maximum);when(maximum.getValue()).thenReturn(20.0);when(target.getHealth()).thenReturn(6.0);
            var event=mock(EntityDamageByEntityEvent.class);when(event.getEntity()).thenReturn(target);when(event.getDamager()).thenReturn(attacker);when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
            var base=new AtomicReference<>(100.0);when(event.getDamage()).thenAnswer(i->base.get());when(event.getFinalDamage()).thenAnswer(i->Math.max(0,base.get()*.6-2));doAnswer(i->{base.set(i.getArgument(0));return null;}).when(event).setDamage(anyDouble());
            new WeaponDamageCaps(plugin).damage(event);assertEquals(weapon==Material.MACE?19.8:14,event.getFinalDamage(),1e-8);verify(target,never()).setHealth(anyDouble());
            when(maximum.getValue()).thenReturn(28.0);base.set(100.0);new WeaponDamageCaps(plugin).damage(event);assertEquals(28*WeaponDamageCaps.fraction(weapon),event.getFinalDamage(),1e-8);
            base.set(10.0);new WeaponDamageCaps(plugin).damage(event);assertEquals(10,base.get());
            when(event.isCancelled()).thenReturn(true);base.set(100.0);new WeaponDamageCaps(plugin).damage(event);assertEquals(100,base.get());
        }
    }
    @Test void otherWeaponsAndZeroDamageStayUntouched(){
        assertEquals(0,WeaponDamageCaps.fraction(Material.NETHERITE_SWORD));assertEquals(.7,WeaponDamageCaps.fraction(Material.WOODEN_SPEAR));
        var event=mock(EntityDamageEvent.class);when(event.getDamage()).thenReturn(40.0);when(event.getFinalDamage()).thenReturn(0.0);WeaponDamageCaps.cap(event,14);verify(event,never()).setDamage(anyDouble());
    }
}

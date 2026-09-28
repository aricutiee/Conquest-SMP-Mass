package dev.turtleroles.events;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShockMaceTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private ShockMace service() {
        when(plugin.getName()).thenReturn("shockSMP");
        return new ShockMace(Map.of());
    }

    private ItemStack mace(ItemMeta meta) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.MACE);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        return item;
    }

    @Test void specifiedEnchantmentLevelsAreExact() {
        assertEquals(Map.of("density", 3, "wind_burst", 2, "unbreaking", 3, "mending", 1),
                ShockMace.requiredSpecs());
    }


    @Test void renamedOrdinaryMaceCannotBecomeSpecial() {
        ShockMace service = service();
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer tags = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(tags);
        when(meta.getEnchants()).thenReturn(Map.of());
        assertFalse(service.isGenuine(mace(meta)));
    }

    @Test void genuineMaceIsRecognized() {
        ShockMace service = service();
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer tags = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(tags);
        when(tags.get(new NamespacedKey("shocksmp", "shock_mace"), PersistentDataType.BYTE)).thenReturn((byte) 1);
        when(tags.get(new NamespacedKey("shocksmp", "shock_mace_instance"), PersistentDataType.STRING))
                .thenReturn(UUID.randomUUID().toString());
        Map<Enchantment, Integer> enchanted = new java.util.HashMap<>();
        enchanted.put(null, 4);
        when(meta.getEnchants()).thenReturn(enchanted);
        ItemStack item = mace(meta);
        assertTrue(service.isGenuine(item));
    }

}

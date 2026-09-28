package dev.turtleroles.migration;
import org.junit.jupiter.api.Test;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataContainer;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class RetiredShockCleanupTest {
    private ItemStack item(ItemMeta meta, boolean crystal) {
        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        var data = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.has(new NamespacedKey("shocksmppowersystem", "shock_type"))).thenReturn(crystal);
        return item;
    }
    @Test void onlyTaggedCrystalsQualify() {
        assertFalse(RetiredShockCleanup.containsCrystal(null));
        assertFalse(RetiredShockCleanup.containsCrystal(item(mock(ItemMeta.class), false)));
        assertTrue(RetiredShockCleanup.containsCrystal(item(mock(ItemMeta.class), true)));
    }
    @Test void findsCrystalsInsideBundles() {
        var bundle = mock(BundleMeta.class);
        ItemStack container = item(bundle, false);
        ItemStack crystal = item(mock(ItemMeta.class), true);
        when(bundle.getItems()).thenReturn(java.util.List.of(crystal));
        assertTrue(RetiredShockCleanup.containsCrystal(container));
    }
}

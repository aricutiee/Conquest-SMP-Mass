package dev.turtleroles.service;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;

class KitAndItemRulesTest {
    @BeforeEach void start(){MockBukkit.mock();}
    @AfterEach void stop(){MockBukkit.unmock();}
    @Test void fullInventoryRejectsWholeKitWithoutTouchingOriginalStacks(){
        var old=new ItemStack(Material.DIAMOND,63);
        var reward=new ItemStack(Material.DIAMOND,2);
        assertNull(KitInventoryPlan.fit(new ItemStack[]{old},new ItemStack[]{reward},64));
        assertEquals(63,old.getAmount());assertEquals(2,reward.getAmount());
    }
    @Test void partialStacksAndEmptySlotsReceiveOnlyContents(){
        var old=new ItemStack(Material.DIAMOND,60);
        var plan=KitInventoryPlan.fit(new ItemStack[]{old,null},new ItemStack[]{new ItemStack(Material.DIAMOND,10)},64);
        assertNotNull(plan);assertEquals(64,plan[0].getAmount());assertEquals(6,plan[1].getAmount());
        assertEquals(60,old.getAmount());
        assertTrue(java.util.Arrays.stream(plan).allMatch(i->i.getType()==Material.DIAMOND));
    }
    @Test void unstackableItemsRequireSeparateSlotsAndMetadataIsKept(){
        var sword=new ItemStack(Material.DIAMOND_SWORD);var meta=sword.getItemMeta();meta.displayName(Component.text("Special"));sword.setItemMeta(meta);
        assertNull(KitInventoryPlan.fit(new ItemStack[1],new ItemStack[]{sword,sword},64));
        var plan=KitInventoryPlan.fit(new ItemStack[2],new ItemStack[]{sword,sword},64);
        assertNotNull(plan);assertEquals(sword.getItemMeta(),plan[0].getItemMeta());
    }
    @Test void customNamesAreProtectedButNormalAnvilRenamesAreAllowed(){
        var sword=new ItemStack(Material.NETHERITE_SWORD);var meta=sword.getItemMeta();meta.displayName(Component.text("Warlord's Ratio"));
        meta.getPersistentDataContainer().set(new NamespacedKey("conquestsmp","purge_relic"),PersistentDataType.STRING,"sword");sword.setItemMeta(meta);
        assertFalse(CustomItemNames.renamed(sword,sword.clone()));
        var renamed=sword.clone();meta=renamed.getItemMeta();meta.displayName(Component.text("Other"));renamed.setItemMeta(meta);
        assertTrue(CustomItemNames.renamed(sword,renamed));
        assertFalse(CustomItemNames.renamed(new ItemStack(Material.DIAMOND_SWORD),renamed));
        assertTrue(CustomItemNames.custom(new ItemStack(Material.DRAGON_EGG)));
    }
}

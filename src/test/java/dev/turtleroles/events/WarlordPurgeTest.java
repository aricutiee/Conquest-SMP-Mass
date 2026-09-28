package dev.turtleroles.events;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

class WarlordPurgeTest {
    ServerMock server;JavaPlugin plugin;WarlordRelics relics;EventRewardGuard guard;
    @BeforeEach void setup(){server=MockBukkit.mock();plugin=MockBukkit.createMockPlugin();relics=new WarlordRelics(plugin,new YamlConfiguration());guard=new EventRewardGuard(plugin);}
    @AfterEach void close(){MockBukkit.unmock();}
    @Test void gearUsesFinalEnchantmentsAndSevenDistinctPieces(){
        assertEquals(7,WarlordRelics.PIECES.size());
        for(String piece:List.of("helmet","chestplate","leggings","boots"))
            assertEquals(piece.equals("helmet")||piece.equals("boots")?6:5,relics.create(piece).getEnchantmentLevel(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("protection"))));
        assertEquals(6,relics.create("sword").getEnchantmentLevel(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"))));
        var pick=relics.create("pickaxe");assertTrue(pick.getItemMeta().isUnbreakable());
        assertEquals(5,pick.getEnchantmentLevel(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"))));
        assertEquals(3,pick.getEnchantmentLevel(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fortune"))));
        assertTrue(EventRewardGuard.reward(pick));
    }
    @Test void enchantmentsCanBeTunedWithoutCodeChanges(){
        var settings=new YamlConfiguration();settings.set("warlord-purge.enchantments.sword.sharpness",6);
        assertEquals(6,new WarlordRelics(plugin,settings).create("sword").getEnchantmentLevel(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"))));
    }
    @Test void miningToggleChangesOncePerCrouchAndPersistsOnTheItem(){
        var player=server.addPlayer();player.getInventory().setItemInMainHand(relics.create("pickaxe"));
        relics.sneak(new PlayerToggleSneakEvent(player,true));assertTrue(WarlordRelics.enabled(player.getInventory().getItemInMainHand()));
        relics.sneak(new PlayerToggleSneakEvent(player,false));assertTrue(WarlordRelics.enabled(player.getInventory().getItemInMainHand()));
        relics.sneak(new PlayerToggleSneakEvent(player,true));assertFalse(WarlordRelics.enabled(player.getInventory().getItemInMainHand()));
    }
    @Test void miningPlanesHaveEightUniqueNeighborsAndNeverRebreakCenter(){
        for(BlockFace face:List.of(BlockFace.UP,BlockFace.DOWN,BlockFace.EAST,BlockFace.WEST,BlockFace.NORTH,BlockFace.SOUTH)) {
            var offsets=WarlordRelics.offsets(face);assertEquals(8,offsets.size());assertEquals(8,offsets.stream().map(Arrays::toString).distinct().count());
            for(int[] offset:offsets){assertFalse(Arrays.equals(new int[]{0,0,0},offset));assertEquals(0,offset[face.getModY()!=0?1:face.getModX()!=0?0:2]);}
        }
    }
    @Test void detectsEggCrownMaceAndNestedBundleButNotOrdinaryItems(){
        assertTrue(EventRewardGuard.contains(new ItemStack(Material.DRAGON_EGG)));
        var crown=new ItemStack(Material.GOLDEN_HELMET);var meta=crown.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("kingscrown","crownsmp_crown"),PersistentDataType.BYTE,(byte)1);crown.setItemMeta(meta);
        assertTrue(EventRewardGuard.contains(crown));assertTrue(EventRewardGuard.contains(new ShockMace(Map.of()).create()));
        var bundle=new ItemStack(Material.BUNDLE);BundleMeta bm=(BundleMeta)bundle.getItemMeta();bm.setItems(List.of(crown));bundle.setItemMeta(bm);
        assertTrue(EventRewardGuard.contains(bundle));assertFalse(EventRewardGuard.contains(new ItemStack(Material.DIAMOND)));
    }
    @Test void blocksShiftClicksAndHotbarSwapsIntoVanillaEnderStorage(){
        var player=server.addPlayer();var view=player.openInventory(player.getEnderChest());
        player.getInventory().setItem(0,new ItemStack(Material.DRAGON_EGG));
        // A direct click event fixture exposes the actual bottom slot independent of MockBukkit raw-slot mapping.
        var mocked=mock(InventoryClickEvent.class);when(mocked.getView()).thenReturn(view);when(mocked.getWhoClicked()).thenReturn(player);
        when(mocked.getClickedInventory()).thenReturn(view.getBottomInventory());when(mocked.getCurrentItem()).thenReturn(new ItemStack(Material.DRAGON_EGG));when(mocked.getHotbarButton()).thenReturn(-1);
        when(mocked.isShiftClick()).thenReturn(true);guard.click(mocked);verify(mocked).setCancelled(true);
        var hotbar=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,0,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,0);
        guard.click(hotbar);assertTrue(hotbar.isCancelled());
    }
    @Test void blocksDepositsButAllowsLootWithdrawalAndOrdinaryStorage(){
        var player=server.addPlayer();var chest=server.createInventory(null,54);var view=player.openInventory(chest);
        var egg=new ItemStack(Material.DRAGON_EGG);
        var deposit=mock(InventoryClickEvent.class);when(deposit.getView()).thenReturn(view);when(deposit.getWhoClicked()).thenReturn(player);
        when(deposit.getClickedInventory()).thenReturn(chest);when(deposit.getHotbarButton()).thenReturn(-1);
        when(deposit.getCursor()).thenReturn(egg);when(deposit.getAction()).thenReturn(InventoryAction.PLACE_ALL);
        guard.click(deposit);verify(deposit).setCancelled(true);
        var withdraw=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,0,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);
        chest.setItem(0,egg);guard.click(withdraw);assertFalse(withdraw.isCancelled());
        reset(deposit);when(deposit.getView()).thenReturn(view);when(deposit.getWhoClicked()).thenReturn(player);
        when(deposit.getClickedInventory()).thenReturn(chest);when(deposit.getHotbarButton()).thenReturn(-1);
        when(deposit.getCursor()).thenReturn(new ItemStack(Material.DIAMOND));when(deposit.getAction()).thenReturn(InventoryAction.PLACE_ALL);
        guard.click(deposit);verify(deposit,never()).setCancelled(true);
    }
    @Test void bundlesHoppersAndBookshelvesCannotHideRewards(){
        var player=server.addPlayer();player.openInventory(server.createInventory(null,27));var egg=new ItemStack(Material.DRAGON_EGG);var bundle=new ItemStack(Material.BUNDLE);
        for(boolean reverse:List.of(false,true)){
            var click=mock(InventoryClickEvent.class);when(click.getView()).thenReturn(player.getOpenInventory());when(click.getWhoClicked()).thenReturn(player);
            when(click.getHotbarButton()).thenReturn(-1);when(click.isRightClick()).thenReturn(true);
            when(click.getCursor()).thenReturn(reverse?bundle:egg);when(click.getCurrentItem()).thenReturn(reverse?egg:bundle);
            guard.click(click);verify(click).setCancelled(true);
        }
        var item=mock(Item.class);when(item.getItemStack()).thenReturn(egg);
        var pickup=new InventoryPickupItemEvent(server.createInventory(null,9),item);guard.pickup(pickup);assertTrue(pickup.isCancelled());
        var move=new InventoryMoveItemEvent(player.getInventory(),egg,server.createInventory(null,9),true);guard.move(move);assertTrue(move.isCancelled());
        var book=new ItemStack(Material.BOOK);var meta=book.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("shocksmp","event_reward_issued"),PersistentDataType.BYTE,(byte)1);book.setItemMeta(meta);
        var block=player.getWorld().getBlockAt(0,60,0);block.setType(Material.CHISELED_BOOKSHELF);
        var interact=new org.bukkit.event.player.PlayerInteractEvent(player,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,book,block,BlockFace.UP);
        guard.insert(interact);assertTrue(interact.isCancelled());
    }
    @Test void returnsPreviouslyStoredRewardsWithoutDestroyingThem(){
        var player=server.addPlayer();player.getEnderChest().setItem(4,new ItemStack(Material.DRAGON_EGG));
        guard.evict(player,player.getEnderChest());assertNull(player.getEnderChest().getItem(4));assertTrue(player.getInventory().contains(Material.DRAGON_EGG));
    }
    @Test void protectsGroundRewardsButVoidRemovesAndAnnounces(){
        var player=server.addPlayer();Item item=mock(Item.class);when(item.getItemStack()).thenReturn(new ItemStack(Material.DRAGON_EGG));
        var lava=mock(EntityDamageEvent.class);when(lava.getEntity()).thenReturn(item);when(lava.getCause()).thenReturn(EntityDamageEvent.DamageCause.LAVA);
        guard.damage(lava);verify(lava).setCancelled(true);verify(item,never()).remove();
        var despawn=mock(ItemDespawnEvent.class);when(despawn.getEntity()).thenReturn(item);guard.despawn(despawn);verify(despawn).setCancelled(true);
        var intoVoid=mock(EntityDamageEvent.class);when(intoVoid.getEntity()).thenReturn(item);when(intoVoid.getCause()).thenReturn(EntityDamageEvent.DamageCause.VOID);
        guard.damage(intoVoid);verify(item).remove();assertNotNull(player.nextComponentMessage());
    }
    @Test void warlordLoadoutUsesHelmetAndSevenRelicsInsteadOfCrownAndMace(){
        var settings=new YamlConfiguration();var equipment=new WarlordEquipment(plugin,settings);
        var kit=new JuggernautLoadout(settings,equipment,new ShockMace(Map.of()),new StrengthPotions(plugin)).create(true,null);
        assertEquals(Material.NETHERITE_HELMET,kit.armor().getLast().item().getType());
        assertEquals(7,java.util.stream.Stream.concat(kit.storage().stream(),kit.armor().stream()).filter(e->e.mythic()).count());
        assertFalse(kit.storage().stream().anyMatch(e->e.item().getType()==Material.MACE));equipment.shutdown();
    }
    @Test void huntChestAndRewardSurviveRestartAndChestIsProtected() throws Exception {
        var world=server.addSimpleWorld("purge-world");
        var state=new YamlConfiguration();state.set("event",UUID.randomUUID().toString());
        state.set("pieces.sword.item",relics.create("sword"));state.set("pieces.sword.status","pending");
        var file=new java.io.File(plugin.getDataFolder(),"warlord-hunt.yml");state.save(file);
        var hunt=new WarlordHunt(plugin,new YamlConfiguration(),relics);
        var block=world.getBlockAt(100,70,100);hunt.place("sword",block);
        assertTrue(hunt.outstanding());assertEquals(Material.CHEST,block.getType());
        var inventory=((org.bukkit.block.Chest)block.getState()).getBlockInventory();
        assertEquals(Material.NETHERITE_SWORD,inventory.getItem(13).getType());
        var player=server.addPlayer();var broken=new org.bukkit.event.block.BlockBreakEvent(block,player);hunt.breakChest(broken);assertTrue(broken.isCancelled());
        hunt.close();hunt=new WarlordHunt(plugin,new YamlConfiguration(),relics);
        assertTrue(hunt.outstanding());assertEquals("ready",YamlConfiguration.loadConfiguration(file).getString("pieces.sword.status"));
        assertEquals(1,inventory.getItem(13).getAmount());hunt.close();
    }
}

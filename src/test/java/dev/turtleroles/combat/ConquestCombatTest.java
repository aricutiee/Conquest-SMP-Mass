package dev.turtleroles.combat;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.bossbar.BossBar;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
class ConquestCombatTest {
    ServerMock server;
    JavaPlugin host;
    ConquestCombat combat;
    Player a, b;
    AtomicLong now = new AtomicLong(100_000);
    YamlConfiguration settings = new YamlConfiguration();
    @BeforeEach void setup() {
        server = MockBukkit.mock(); host = MockBukkit.createMockPlugin();
        combat = new ConquestCombat(host, settings, now::get);
        server.getPluginManager().registerEvents(combat, host);
        a = player("a"); b = player("b");
    }
    Player player(String name) {
        var original = server.addPlayer(name);
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(original.getUniqueId());
        when(p.getServer()).thenReturn(server);
        when(p.getPersistentDataContainer()).thenReturn(original.getPersistentDataContainer());
        when(p.getWorld()).thenReturn(original.getWorld());
        p.getWorld().setPVP(true);
        when(p.getInventory()).thenReturn(original.getInventory());
        when(p.getActiveItem()).thenReturn(new ItemStack(Material.AIR));
        return p;
    }
    @AfterEach void teardown() { MockBukkit.unmock(); }
    EntityDamageByEntityEvent hit(Entity attacker, Entity victim, double amount) {
        var event = new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, amount);
        server.getPluginManager().callEvent(event); return event;
    }
    @Test void bothPlayersTaggedAndRefreshForExactly45Seconds() {
        hit(a,b,2); assertTrue(combat.tagged(a)); assertTrue(combat.tagged(b));
        now.addAndGet(44_999); assertTrue(combat.tagged(a));
        hit(a,b,2); now.addAndGet(44_999); assertTrue(combat.tagged(b));
        now.incrementAndGet(); assertFalse(combat.tagged(a)); assertFalse(combat.tagged(b));
    }
    @Test void cancelledZeroSelfMobAndPvpDisabledDoNotTag() {
        Listener protection = new Listener() { @EventHandler(priority=EventPriority.HIGHEST)
            public void cancel(EntityDamageByEntityEvent e) { e.setCancelled(true); } };
        server.getPluginManager().registerEvents(protection, host);
        assertTrue(hit(a,b,4).isCancelled()); assertFalse(combat.tagged(a));
        HandlerList.unregisterAll(protection);
        hit(a,b,0); hit(a,a,4); hit(mock(Zombie.class),b,4);
        a.getWorld().setPVP(false); hit(a,b,4);
        assertFalse(combat.tagged(a)); assertFalse(combat.tagged(b));
    }
    @Test void projectileShooterIsTagged() {
        Arrow arrow = mock(Arrow.class); when(arrow.getShooter()).thenReturn(a);
        hit(arrow,b,3); assertTrue(combat.tagged(a)); assertTrue(combat.tagged(b));
    }
    @Test void regularPlayerCommandsStayBlockedUntilExpiry() {
        hit(a,b,2);when(a.isOp()).thenReturn(false);
        for(String command:java.util.List.of("/home","/essentials:home base","/minecraft:teleport a","/spawn","/util")) {
            var event=new PlayerCommandPreprocessEvent(a,command);combat.command(event);assertTrue(event.isCancelled());
        }
        now.addAndGet(45_000);var allowed=new PlayerCommandPreprocessEvent(a,"/home");combat.command(allowed);assertFalse(allowed.isCancelled());
    }
    @Test void operatorsCanUseCommandsWhileStillCombatTagged() {
        hit(a,b,2);when(a.isOp()).thenReturn(true);
        var event=new PlayerCommandPreprocessEvent(a,"/minecraft:teleport a");combat.command(event);
        assertFalse(event.isCancelled());assertTrue(combat.tagged(a));
        when(a.isOp()).thenReturn(false);
        var denied=new PlayerCommandPreprocessEvent(a,"/home");combat.command(denied);assertTrue(denied.isCancelled());
    }
    @Test void bossbarCountsDownAndDisappears() {
        hit(a,b,2);
        var capture = ArgumentCaptor.forClass(BossBar.class); verify(a).showBossBar(capture.capture());
        BossBar bar = capture.getValue(); assertEquals(1f,bar.progress());
        now.addAndGet(22_500); combat.refresh(a); assertEquals(.5f,bar.progress(),.001);
        now.addAndGet(22_500); combat.refresh(a); verify(a).hideBossBar(bar);
    }
    @Test void tagSurvivesQuitAndNewModuleInstance() {
        hit(a,b,2); combat.quit(new PlayerQuitEvent(a, net.kyori.adventure.text.Component.empty()));
        ConquestCombat restored = new ConquestCombat(host, settings, now::get);
        assertTrue(restored.tagged(a)); now.addAndGet(45_000); assertFalse(restored.tagged(a));
    }
    @Test void glidingAndTenSecondsAfterwardsBlockMaceAgainstAnyTarget() {
        a.getInventory().setItemInMainHand(new ItemStack(Material.MACE));
        when(a.isGliding()).thenReturn(true);
        assertTrue(hit(a,b,100).isCancelled()); assertFalse(combat.tagged(b));
        combat.glideChanged(new EntityToggleGlideEvent(a,false)); when(a.isGliding()).thenReturn(false);
        now.addAndGet(9_999); assertTrue(hit(a,mock(Zombie.class),100).isCancelled());
        now.incrementAndGet(); assertFalse(hit(a,b,100).isCancelled());
    }
    @Test void switchingWeaponWorksAndEquippingElytraAloneDoesNotLockMace() {
        a.getInventory().setChestplate(new ItemStack(Material.ELYTRA));
        a.getInventory().setItemInMainHand(new ItemStack(Material.MACE));
        assertFalse(combat.maceLocked(a));
        combat.glideChanged(new EntityToggleGlideEvent(a,false));
        a.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        assertFalse(hit(a,b,5).isCancelled());
    }
    @Test void taggedPlayersCannotStartGlidingOrUseTridentMeleeLaunchOrRiptide() {
        hit(a,b,2);
        var glide = new EntityToggleGlideEvent(a,true); combat.glideGuard(glide); assertTrue(glide.isCancelled());
        a.getInventory().setItemInMainHand(new ItemStack(Material.TRIDENT));
        assertTrue(hit(a,b,8).isCancelled());
        Trident trident = mock(Trident.class); when(trident.getShooter()).thenReturn(a);
        var launch = new ProjectileLaunchEvent(trident); combat.launch(launch); assertTrue(launch.isCancelled());
        var riptide = new PlayerRiptideEvent(a,new ItemStack(Material.TRIDENT),new org.bukkit.util.Vector());
        combat.riptide(riptide); assertTrue(riptide.isCancelled());
        var interact = new PlayerInteractEvent(a,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                new ItemStack(Material.TRIDENT),null,org.bukkit.block.BlockFace.SELF,EquipmentSlot.OFF_HAND);
        combat.interact(interact); assertEquals(Event.Result.DENY,interact.useItemInHand());
        now.addAndGet(45_000); assertFalse(hit(a,b,8).isCancelled());
    }
    @Test void tagStopsCurrentFlightAndChargedTridentWithoutDeletingItems() {
        when(a.isGliding()).thenReturn(true);
        when(a.getActiveItem()).thenReturn(new ItemStack(Material.TRIDENT));
        hit(b,a,2); verify(a).setGliding(false); verify(a).clearActiveItem();
        when(a.isGliding()).thenReturn(false); assertTrue(combat.maceLocked(a));
    }
    @Test void crystalAndAnchorPlayerDamageBlockedButBedsTntAndMobDamageRemain() {
        var crystal = new EntityDamageByEntityEvent(mock(EnderCrystal.class),b,
                EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,20);
        combat.guardDamage(crystal); assertTrue(crystal.isCancelled());
        BlockState anchor = mock(BlockState.class); when(anchor.getType()).thenReturn(Material.RESPAWN_ANCHOR);
        var anchorHit = new EntityDamageByBlockEvent(null,anchor,b,EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,mock(DamageSource.class),20);
        combat.guardDamage(anchorHit); assertTrue(anchorHit.isCancelled());
        when(anchor.getType()).thenReturn(Material.RED_BED);
        var bedHit = new EntityDamageByBlockEvent(null,anchor,b,EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,mock(DamageSource.class),20);
        combat.guardDamage(bedHit); assertFalse(bedHit.isCancelled());
        var tnt = new EntityDamageByEntityEvent(mock(TNTPrimed.class),b,EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,20);
        combat.guardDamage(tnt); assertFalse(tnt.isCancelled());
        var mob = new EntityDamageByEntityEvent(mock(EnderCrystal.class),mock(Zombie.class),EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,20);
        combat.guardDamage(mob); assertFalse(mob.isCancelled());
    }
    @Test void ordinarySpearsChorusAndMaceMeleeRemainUsable() {
        for(Material item : new Material[]{Material.NETHERITE_SPEAR,Material.CHORUS_FRUIT,Material.MACE}) {
            a.getInventory().setItemInMainHand(new ItemStack(item));
            assertFalse(hit(a,b,1).isCancelled(),item.name());
        }
    }
    ProjectileLaunchEvent pearl(Player player) {
        EnderPearl pearl = mock(EnderPearl.class);
        when(pearl.getShooter()).thenReturn(player);
        var event = new ProjectileLaunchEvent(pearl);
        server.getPluginManager().callEvent(event);
        return event;
    }
    @Test void pearlsCannotLaunchInEitherHandEvenWithoutCombatOrAfterWaiting() {
        assertTrue(pearl(a).isCancelled());
        now.addAndGet(60_000);assertTrue(pearl(a).isCancelled());
        for(EquipmentSlot hand:new EquipmentSlot[]{EquipmentSlot.HAND,EquipmentSlot.OFF_HAND}) {
            var use=new PlayerInteractEvent(a,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,new ItemStack(Material.ENDER_PEARL),null,org.bukkit.block.BlockFace.SELF,hand);
            combat.interact(use);assertEquals(Event.Result.DENY,use.useItemInHand());
        }
    }
    @Test void pearlPickupIsDeniedWithoutRemovingItemAndOtherItemsWork() {
        Item item=mock(Item.class);when(item.getItemStack()).thenReturn(new ItemStack(Material.ENDER_PEARL));
        var pickup=new EntityPickupItemEvent(a,item,0);combat.pickup(pickup);assertTrue(pickup.isCancelled());verify(item,never()).remove();
        when(item.getItemStack()).thenReturn(new ItemStack(Material.DIAMOND));
        var allowed=new EntityPickupItemEvent(a,item,0);combat.pickup(allowed);assertFalse(allowed.isCancelled());
    }
    @Test void pearlsAlreadyInFlightCannotTeleportButOtherTeleportsWork() {
        var location=new Location(a.getWorld(),0,65,0);
        var pearl=new PlayerTeleportEvent(a,location,location,PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        combat.pearlTeleport(pearl);assertTrue(pearl.isCancelled());
        var command=new PlayerTeleportEvent(a,location,location,PlayerTeleportEvent.TeleportCause.COMMAND);
        combat.pearlTeleport(command);assertFalse(command.isCancelled());
    }
    @Test void maceRecoveryHasItsOwnBossbarOutsideCombat() {
        combat.glideChanged(new EntityToggleGlideEvent(a,false));combat.refresh(a);
        var capture=ArgumentCaptor.forClass(BossBar.class);verify(a).showBossBar(capture.capture());
        BossBar bar=capture.getValue();assertEquals(1f,bar.progress());
        now.addAndGet(5_000);combat.refresh(a);assertEquals(.5f,bar.progress(),.001);
        now.addAndGet(5_000);combat.refresh(a);verify(a).hideBossBar(bar);
    }

    @Test void fourStaffRolesBypassStoredCombatMaceAndPearlRestrictions(){
        var plugin=mock(dev.turtleroles.TurtleRolesPlugin.class);var roles=mock(dev.turtleroles.service.RoleService.class);
        when(plugin.roleService()).thenReturn(roles);combat=new ConquestCombat(plugin,settings,now::get);
        a.getPersistentDataContainer().set(new NamespacedKey("conquestsmp","combat_until"),org.bukkit.persistence.PersistentDataType.LONG,now.get()+45_000);
        a.getPersistentDataContainer().set(new NamespacedKey("conquestsmp","mace_until"),org.bukkit.persistence.PersistentDataType.LONG,now.get()+10_000);
        when(a.isOp()).thenReturn(true);
        for(var role:dev.turtleroles.role.Role.values()){
            when(roles.roleOf(a.getUniqueId())).thenReturn(role);
            boolean bypass=dev.turtleroles.service.GameplayBypass.role(role);
            assertEquals(!bypass,combat.tagged(a),role.name());assertEquals(!bypass,combat.maceLocked(a),role.name());
            var pearl=new PlayerTeleportEvent(a,new Location(a.getWorld(),0,64,0),new Location(a.getWorld(),20,64,20),PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
            combat.pearlTeleport(pearl);assertEquals(!bypass,pearl.isCancelled(),role.name());
        }
    }
    @Test void bypassNeverUncancelsOtherPluginsDamage(){
        var plugin=mock(dev.turtleroles.TurtleRolesPlugin.class);var roles=mock(dev.turtleroles.service.RoleService.class);
        when(plugin.roleService()).thenReturn(roles);when(roles.roleOf(a.getUniqueId())).thenReturn(dev.turtleroles.role.Role.ADMIN);
        combat=new ConquestCombat(plugin,settings,now::get);
        var hit=new EntityDamageByEntityEvent(a,b,EntityDamageEvent.DamageCause.ENTITY_ATTACK,2);hit.setCancelled(true);
        combat.guardDamage(hit);combat.damaged(hit);assertTrue(hit.isCancelled());assertFalse(combat.tagged(b));
    }

}

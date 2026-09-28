package dev.turtleroles.survival;
import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.combat.ConquestCombat;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.*;
import java.nio.file.Path;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SpawnAreaTest {
    @TempDir Path temp;ServerMock server;TurtleRolesPlugin plugin;SurvivalModule module;World world;Player player;SpawnArea area;
    @BeforeEach void setup(){
        server=MockBukkit.mock();world=server.addSimpleWorld("world_terralith");player=server.addPlayer();
        plugin=mock(TurtleRolesPlugin.class);when(plugin.getDataFolder()).thenReturn(temp.toFile());when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());when(plugin.combat()).thenReturn(mock(ConquestCombat.class));
        when(plugin.isEnabled()).thenReturn(true);when(plugin.getServer()).thenReturn(server);when(plugin.getName()).thenReturn("ConquestSMP");
        module=new SurvivalModule(plugin);area=module.spawnArea();
        module.data.set("spawn-area.first",new Location(world,10,60,20));module.data.set("spawn-area.second",new Location(world,19,80,39));
    }
    @AfterEach void close(){area.close();MockBukkit.unmock();}
    @Test void selectionIncludesAllAltitudesButRespectsHorizontalEdgesAndWorld(){
        assertTrue(area.inside(new Location(world,10,60,20)));assertTrue(area.inside(new Location(world,19.99,80.99,39.99)));
        assertFalse(area.inside(new Location(world,20,70,25)));for(double y:new double[]{-30000000,-65,81,320,30000000})assertTrue(area.inside(new Location(world,15,y,25)));
        assertFalse(area.inside(new Location(server.addSimpleWorld("other"),15,70,25)));
        assertEquals(20,area.region().borderSize());assertEquals(15,area.region().centerX());assertEquals(30,area.region().centerZ());
    }
    @Test void combatCannotBypassAreaAboveOrBelowBuildLimits(){
        when(plugin.combat().tagged(player)).thenReturn(true);
        for(double y:new double[]{-1000,1000}){
            var from=new Location(world,9,y,25);var to=new Location(world,10,y,25);
            var move=new PlayerMoveEvent(player,from,to);area.move(move);assertEquals(from,move.getTo());
            var teleport=new PlayerTeleportEvent(player,from,to);area.teleport(teleport);assertTrue(teleport.isCancelled());
        }
    }
    @Test void selectionRequiresAdminAndPersistsTwoCorners(){
        module.data.set("spawn-area",null);area.command(player,new String[]{"area"});
        var first=new PlayerInteractEvent(player,Action.LEFT_CLICK_BLOCK,null,world.getBlockAt(1,50,1),org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);
        area.select(first);assertNull(area.region());
        player.setOp(true);area.command(player,new String[]{"area"});area.select(first);
        area.select(new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,null,world.getBlockAt(5,90,8),org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND));
        assertNotNull(area.region());assertEquals(area.region(),new SurvivalModule(plugin).spawnArea().region());
    }
    @Test void taggedEntryAndTeleportAreBlockedButExitAndNormalEntryRemainAllowed(){
        var from=new Location(world,9,70,25);var to=new Location(world,10,70,25);
        when(plugin.combat().tagged(player)).thenReturn(true);
        var move=new PlayerMoveEvent(player,from,to);area.move(move);assertEquals(from,move.getTo());
        var teleport=new PlayerTeleportEvent(player,from,to);area.teleport(teleport);assertTrue(teleport.isCancelled());
        var exit=new PlayerMoveEvent(player,to,from);area.move(exit);assertEquals(from,exit.getTo());
        when(plugin.combat().tagged(player)).thenReturn(false);var normal=new PlayerMoveEvent(player,from,to);area.move(normal);assertEquals(to,normal.getTo());
    }
    @Test void pvpBlockedInBothDirectionsButAllowedOutside(){
        var attacker=server.addPlayer();var inside=new Location(world,15,70,25);var outside=new Location(world,0,70,0);
        for(boolean inward:new boolean[]{true,false}){
            player.teleport(inward?inside:outside);attacker.teleport(inward?outside:inside);
            var damage=mock(EntityDamageByEntityEvent.class);when(damage.getEntity()).thenReturn(player);when(damage.getDamager()).thenReturn(attacker);
            area.damage(damage);verify(damage).setCancelled(true);
        }
        player.teleport(outside);attacker.teleport(outside);var damage=mock(EntityDamageByEntityEvent.class);when(damage.getEntity()).thenReturn(player);when(damage.getDamager()).thenReturn(attacker);
        area.damage(damage);verify(damage,never()).setCancelled(true);
    }
    @Test void projectilesRememberProtectedLaunchAfterShooterLeaves(){
        player.teleport(new Location(world,15,70,25));
        var arrow=mock(Arrow.class);when(arrow.getShooter()).thenReturn(player);when(arrow.getLocation()).thenReturn(player.getLocation());when(arrow.getPersistentDataContainer()).thenReturn(player.getPersistentDataContainer());
        area.launch(new ProjectileLaunchEvent(arrow));player.teleport(new Location(world,0,70,0));when(arrow.getLocation()).thenReturn(player.getLocation());
        var victim=server.addPlayer();victim.teleport(player.getLocation());var damage=mock(EntityDamageByEntityEvent.class);when(damage.getEntity()).thenReturn(victim);when(damage.getDamager()).thenReturn(arrow);
        area.damage(damage);verify(damage).setCancelled(true);
    }
    @Test void splashAndLingeringEffectsCannotCrossBoundary(){
        var shooter=server.addPlayer();shooter.teleport(new Location(world,0,70,0));player.teleport(new Location(world,15,70,25));
        var potion=mock(ThrownPotion.class);when(potion.getLocation()).thenReturn(shooter.getLocation());when(potion.getShooter()).thenReturn(shooter);when(potion.getPersistentDataContainer()).thenReturn(shooter.getPersistentDataContainer());
        var splash=mock(PotionSplashEvent.class);when(splash.getPotion()).thenReturn(potion);when(splash.getAffectedEntities()).thenReturn(java.util.List.of(player));area.splash(splash);verify(splash).setIntensity(player,0);
        var cloud=mock(AreaEffectCloud.class);when(cloud.getLocation()).thenReturn(shooter.getLocation());when(cloud.getSource()).thenReturn(shooter);when(cloud.getPersistentDataContainer()).thenReturn(shooter.getPersistentDataContainer());
        var event=mock(AreaEffectCloudApplyEvent.class);when(event.getEntity()).thenReturn(cloud);var affected=new java.util.ArrayList<LivingEntity>(java.util.List.of(player,shooter));when(event.getAffectedEntities()).thenReturn(affected);
        area.cloud(event);assertEquals(java.util.List.of(shooter),affected);
    }
    @Test void selectedAreaReplacesOldColumnsAndBlocksGrief(){
        var guard=new SpawnProtection(module);var inside=world.getBlockAt(15,70,25);var outside=world.getBlockAt(20,90,25);
        var broken=new BlockBreakEvent(inside,player);guard.breakBlock(broken);assertTrue(broken.isCancelled());assertFalse(guard.protectedAt(outside));
        var explosion=mock(EntityExplodeEvent.class);var blocks=new java.util.ArrayList<>(java.util.List.of(inside,outside));when(explosion.blockList()).thenReturn(blocks);guard.entityExplosion(explosion);assertEquals(java.util.List.of(outside),blocks);
    }
    @Test void borderSurvivesRestartAndRestoresOriginalCenterAndSizeOnLaunch(){
        player.setOp(true);var border=world.getWorldBorder();border.setCenter(100,200);border.setSize(15000);
        area.command(player,new String[]{"border"});assertEquals(20,border.getSize());assertEquals(15,border.getCenter().getX());
        var reloaded=new SurvivalModule(plugin);border.setSize(15000);reloaded.spawnArea().applyBorder();assertEquals(20,border.getSize());
        when(plugin.smpStarted()).thenReturn(true);reloaded.spawnArea().applyBorder();assertEquals(15000,border.getSize());assertEquals(100,border.getCenter().getX());assertEquals(200,border.getCenter().getZ());
        assertFalse(new SurvivalModule(plugin).data.getBoolean("spawn-border.active"));
        reloaded.spawnArea().command(player,new String[]{"border"});assertEquals(15000,border.getSize());
    }

    @Test void staffBypassUsesStoredRoleAndChangesImmediately(){
        var roles=mock(dev.turtleroles.service.RoleService.class);when(plugin.roleService()).thenReturn(roles);
        when(plugin.combat().tagged(player)).thenReturn(true);
        var guard=new SpawnProtection(module);player.setOp(true);
        for(var role:dev.turtleroles.role.Role.values()){
            when(roles.roleOf(player.getUniqueId())).thenReturn(role);
            boolean bypass=dev.turtleroles.service.GameplayBypass.role(role);
            var broken=new BlockBreakEvent(world.getBlockAt(15,70,25),player);guard.breakBlock(broken);
            assertEquals(!bypass,broken.isCancelled(),role.name());
            var from=new Location(world,9,70,25);var to=new Location(world,10,70,25);
            var teleport=new PlayerTeleportEvent(player,from,to);area.teleport(teleport);assertEquals(!bypass,teleport.isCancelled(),role.name());
            var move=new PlayerMoveEvent(player,from,to);area.move(move);assertEquals(bypass?to:from,move.getTo(),role.name());
        }
    }
    @Test void adminCanAttackInsideSpawnButMemberCannotAttackProtectedStaff(){
        var roles=mock(dev.turtleroles.service.RoleService.class);when(plugin.roleService()).thenReturn(roles);
        when(roles.roleOf(player.getUniqueId())).thenReturn(dev.turtleroles.role.Role.ADMIN);
        var member=server.addPlayer();member.teleport(new Location(world,15,70,25));player.teleport(member.getLocation());
        var hit=mock(EntityDamageByEntityEvent.class);when(hit.getEntity()).thenReturn(member);when(hit.getDamager()).thenReturn(player);
        area.damage(hit);verify(hit,never()).setCancelled(true);
        var reverse=mock(EntityDamageByEntityEvent.class);when(reverse.getEntity()).thenReturn(player);when(reverse.getDamager()).thenReturn(member);
        area.damage(reverse);verify(reverse).setCancelled(true);
    }


    @Test void staffCanEnterClosedDimensionsButSserCannot(){
        var roles=mock(dev.turtleroles.service.RoleService.class);when(plugin.roleService()).thenReturn(roles);
        var nether=mock(World.class);when(nether.getEnvironment()).thenReturn(World.Environment.NETHER);module.data.set("dimensions.nether",false);
        for(var role:dev.turtleroles.role.Role.values()){
            when(roles.roleOf(player.getUniqueId())).thenReturn(role);
            var event=new PlayerTeleportEvent(player,player.getLocation(),new Location(nether,0,70,0));module.portal(event);
            assertEquals(!dev.turtleroles.service.GameplayBypass.role(role),event.isCancelled());
        }
    }

    @Test void fastVehicleCannotJumpAcrossTheEntireProtectedArea(){
        when(plugin.combat().tagged(player)).thenReturn(true);
        var boat=mock(Boat.class);when(boat.getPassengers()).thenReturn(java.util.List.of(player));
        var from=new Location(world,9,70,25);var to=new Location(world,30,70,25);
        player.teleport(from);
        area.vehicle(new org.bukkit.event.vehicle.VehicleMoveEvent(boat,from,to));
        verify(boat).removePassenger(player);verify(boat).teleport(from);
        assertFalse(area.inside(player.getLocation()));
    }
}

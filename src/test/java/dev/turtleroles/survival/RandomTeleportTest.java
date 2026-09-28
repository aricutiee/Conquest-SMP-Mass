package dev.turtleroles.survival;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RandomTeleportTest {
    @Test void restrictsWorldAndCombatBeforeLoadingChunks(){
        var server=org.mockbukkit.mockbukkit.MockBukkit.mock();
        var host=org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin();
        server.addSimpleWorld("other_world"); var terralith=server.addSimpleWorld("world_terralith");
        var player=server.addPlayer();
        var rtp=new RandomTeleport(host,p->true);
        try {
            rtp.start(player);
            assertTrue(player.nextMessage().contains("Terralith Overworld only"));
            player.teleport(terralith.getSpawnLocation());rtp.start(player);
            assertTrue(player.nextMessage().contains("during combat"));
        } finally {rtp.close();org.mockbukkit.mockbukkit.MockBukkit.unmock();}
    }
    @Test void rangeKeepsEntirePlayerInsideOffsetBorders(){
        for(double center:new double[]{0,-1234.75,781.2})for(double width:new double[]{3,100,15000,30000}){
            int[] r=RandomTeleport.range(center,width);assertNotNull(r);
            assertTrue(r[0]+.5>=center-width/2+1);assertTrue(r[1]+.5<=center+width/2-1);
        }
        assertNull(RandomTeleport.range(0,1));
    }
    @Test void recheckRejectsShrinkingBorder(){
        WorldBorder border=mock(WorldBorder.class);World world=mock(World.class);
        when(border.getCenter()).thenReturn(new Location(world,-100,0,250));when(border.getSize()).thenReturn(100.0);
        Location spot=new Location(world,-60,65,250);assertTrue(RandomTeleport.inside(spot,border));
        when(border.getSize()).thenReturn(50.0);assertFalse(RandomTeleport.inside(spot,border));
    }
    @Test void landingRejectsHazardsWaterAndBlockedHeads(){
        World world=mock(World.class);when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        Block feet=mock(Block.class),head=mock(Block.class),floor=mock(Block.class);
        when(world.getBlockAt(any(Location.class))).thenReturn(feet);when(feet.getRelative(0,1,0)).thenReturn(head);when(feet.getRelative(0,-1,0)).thenReturn(floor);
        when(feet.getType()).thenReturn(Material.AIR);when(head.getType()).thenReturn(Material.AIR);
        when(feet.isPassable()).thenReturn(true);when(head.isPassable()).thenReturn(true);when(floor.getType()).thenReturn(Material.STONE);
        Location spot=new Location(world,.5,65,.5);assertTrue(RandomTeleport.safe(spot));
        for(Material hazard:new Material[]{Material.MAGMA_BLOCK,Material.CACTUS,Material.POWDER_SNOW,Material.LAVA,Material.WATER}){
            when(floor.getType()).thenReturn(hazard);assertFalse(RandomTeleport.safe(spot));
        }
        when(floor.getType()).thenReturn(Material.STONE);when(feet.isLiquid()).thenReturn(true);assertFalse(RandomTeleport.safe(spot));
        when(feet.isLiquid()).thenReturn(false);when(head.isPassable()).thenReturn(false);assertFalse(RandomTeleport.safe(spot));
        assertFalse(RandomTeleport.safe(new Location(world,0,319,0)));
    }
}



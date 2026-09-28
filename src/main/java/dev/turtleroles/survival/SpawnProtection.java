package dev.turtleroles.survival;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.StructureGrowEvent;
import java.util.function.Predicate;

/** Building protection applies only to the explicitly selected spawn cuboid. */
final class SpawnProtection implements Listener {
    private final SurvivalModule module;
    SpawnProtection(SurvivalModule module){this.module=module;}
    boolean protectedAt(Block block) { return module.spawnArea().inside(block.getLocation()); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){if(module.bypass(e.getPlayer()))return;if(protectedAt(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){if(module.bypass(e.getPlayer()))return;if(protectedAt(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void multiPlace(BlockMultiPlaceEvent e){if(module.bypass(e.getPlayer()))return;if(e.getReplacedBlockStates().stream().anyMatch(s->protectedAt(s.getBlock())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hanging(org.bukkit.event.hanging.HangingBreakEvent e){if(e instanceof org.bukkit.event.hanging.HangingBreakByEntityEvent hit&&module.bypass(hit.getRemover()))return;if(protectedAt(e.getEntity().getLocation().getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hangingPlace(org.bukkit.event.hanging.HangingPlaceEvent e){if(module.bypass(e.getPlayer()))return;if(protectedAt(e.getEntity().getLocation().getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void decorationDamage(EntityDamageEvent e){if(e instanceof EntityDamageByEntityEvent hit&&module.bypass(hit.getDamager()))return;if((e.getEntity() instanceof org.bukkit.entity.Hanging || e.getEntity() instanceof org.bukkit.entity.ArmorStand) && protectedAt(e.getEntity().getLocation().getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityExplosion(EntityExplodeEvent e){e.blockList().removeIf(this::protectedAt);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockExplosion(BlockExplodeEvent e){e.blockList().removeIf(this::protectedAt);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fluid(BlockFromToEvent e){if(protectedAt(e.getToBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e){if(protectedAt(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void ignite(BlockIgniteEvent e){if(module.bypass(e.getPlayer()))return;if(protectedAt(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void change(EntityChangeBlockEvent e){if(protectedAt(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void extend(BlockPistonExtendEvent e){if(protectedAt(e.getBlock())||e.getBlocks().stream().anyMatch(b->protectedAt(b)||protectedAt(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void retract(BlockPistonRetractEvent e){if(protectedAt(e.getBlock())||e.getBlocks().stream().anyMatch(b->protectedAt(b)||protectedAt(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucketEmpty(PlayerBucketEmptyEvent e){if(module.bypass(e.getPlayer()))return;if(protectedAt(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void bucketFill(PlayerBucketFillEvent e){if(module.bypass(e.getPlayer()))return;if(protectedAt(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void tree(StructureGrowEvent e){if(module.bypass(e.getPlayer()))return;if(e.getBlocks().stream().anyMatch(b->protectedAt(b.getBlock())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fertilize(BlockFertilizeEvent e){if(module.bypass(e.getPlayer()))return;if(e.getBlocks().stream().anyMatch(b->protectedAt(b.getBlock())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){if(module.bypass(e.getPlayer()))return;
        if(e.getClickedBlock()==null||!protectedAt(e.getClickedBlock()))return;
        if(e.getAction()==Action.PHYSICAL)e.setCancelled(true);
        else if(e.getAction().isRightClick()) {
            String item=e.getItem()==null?"":e.getItem().getType().name();
            String block=e.getClickedBlock().getType().name();
            if(item.endsWith("_AXE")||item.endsWith("_HOE")||item.endsWith("_SHOVEL")||item.equals("SHEARS")||item.equals("FLINT_AND_STEEL")
                    ||item.endsWith("_BUCKET")||item.equals("BONE_MEAL")||item.equals("FIRE_CHARGE")||item.equals("END_CRYSTAL")
                    ||item.endsWith("_DYE")||item.equals("HONEYCOMB")||block.equals("RESPAWN_ANCHOR"))e.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spread(BlockSpreadEvent e){if(protectedAt(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void dispenser(BlockDispenseEvent e){if(protectedAt(e.getBlock())||protectedAt(e.getBlock().getRelative((e.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional d?d.getFacing():org.bukkit.block.BlockFace.SELF))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void armor(PlayerArmorStandManipulateEvent e){if(module.bypass(e.getPlayer()))return;if(protectedAt(e.getRightClicked().getLocation().getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void entityInteract(PlayerInteractEntityEvent e){if(module.bypass(e.getPlayer()))return;if(e.getRightClicked() instanceof org.bukkit.entity.Hanging&&protectedAt(e.getRightClicked().getLocation().getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void sign(SignChangeEvent e){if(module.bypass(e.getPlayer()))return;if(protectedAt(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void harvest(PlayerHarvestBlockEvent e){if(module.bypass(e.getPlayer()))return;if(protectedAt(e.getHarvestedBlock()))e.setCancelled(true);}
}

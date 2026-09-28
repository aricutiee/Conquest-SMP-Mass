package dev.turtleroles.survival;
import dev.turtleroles.TurtleRolesPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Full-height spawn rectangle, PvP boundary and reversible pre-launch border. */
public final class SpawnArea implements Listener {
    private final TurtleRolesPlugin plugin;
    private final SurvivalModule module;
    private final SpawnBarrier barrier;
    public void start(){barrier.start();}
    public void close(){barrier.close();}
    private final Map<UUID,Location> selections=new HashMap<>();
    private final Set<UUID> selecting=new HashSet<>();
    private final Map<UUID,Long> notices=new HashMap<>();
    private final NamespacedKey origin=new NamespacedKey("conquestsmp","safe_spawn_origin");
    public SpawnArea(TurtleRolesPlugin plugin,SurvivalModule module){this.plugin=plugin;this.module=module;this.barrier=new SpawnBarrier(plugin,module);}
    public SpawnRegion region(){
        Location a=module.data.getLocation("spawn-area.first"),b=module.data.getLocation("spawn-area.second");
        if(a==null||b==null||a.getWorld()==null||b.getWorld()==null)return null;
        return SpawnRegion.between(a,b);
    }
    public boolean inside(Location l){var region=region();return region!=null&&region.contains(l);}
    public void command(Player player,String[] args){
        if(!player.hasPermission("conquest.world.admin")){player.sendMessage("Only administrators can edit the spawn area.");return;}
        if(args[0].equalsIgnoreCase("area")){
            if(args.length>1&&args[1].equalsIgnoreCase("cancel")){selecting.remove(player.getUniqueId());selections.remove(player.getUniqueId());player.sendMessage("Spawn selection cancelled.");return;}
            selecting.add(player.getUniqueId());selections.remove(player.getUniqueId());
            player.sendMessage("Left-click the first corner block, then right-click the opposite corner block. Only horizontal boundaries matter; protection covers every height. Use /spawn area cancel to cancel.");return;
        }
        if(args.length>1&&args[1].equalsIgnoreCase("off")){restoreBorder();player.sendMessage("Normal world border restored.");return;}
        if(plugin.smpStarted()){player.sendMessage("The SMP has already started. The pre-launch border cannot be enabled.");return;}
        SpawnRegion area=region();if(area==null){player.sendMessage("Select a spawn area with /spawn area first.");return;}
        World world=Bukkit.getWorld(area.world());
        if(world==null||world.getEnvironment()!=World.Environment.NORMAL){player.sendMessage("The spawn border must be in the overworld.");return;}
        if(!module.data.getBoolean("spawn-border.active")){
            var border=world.getWorldBorder();
            module.data.set("spawn-border.world",world.getUID().toString());
            module.data.set("spawn-border.x",border.getCenter().getX());module.data.set("spawn-border.z",border.getCenter().getZ());
            module.data.set("spawn-border.size",border.getSize());
        }
        module.data.set("spawn-border.active",true);
        if(!module.save()){module.data.set("spawn-border.active",false);player.sendMessage("Could not save the temporary border.");return;}
        applyBorder();player.sendMessage("Spawn border enabled. /smp start restores the normal border; /spawn border off also restores it.");
    }
    public void applyBorder(){
        if(!module.data.getBoolean("spawn-border.active"))return;
        if(plugin.smpStarted()){restoreBorder();return;}
        var area=region();if(area==null)return;World world=Bukkit.getWorld(area.world());if(world==null)return;
        world.getWorldBorder().setCenter(area.centerX(),area.centerZ());world.getWorldBorder().setSize(area.borderSize());
    }
    public void restoreBorder(){
        if(!module.data.getBoolean("spawn-border.active"))return;
        World world=Bukkit.getWorld(UUID.fromString(module.data.getString("spawn-border.world")));
        if(world==null)return;
        world.getWorldBorder().setCenter(module.data.getDouble("spawn-border.x"),module.data.getDouble("spawn-border.z"));
        world.getWorldBorder().setSize(module.data.getDouble("spawn-border.size",15000));
        module.data.set("spawn-border.active",false);
        if(!module.save())module.data.set("spawn-border.active",true); // Retry restoration on next start.
    }
    @EventHandler(priority=EventPriority.LOWEST) public void select(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||!selecting.contains(e.getPlayer().getUniqueId())||e.getClickedBlock()==null)return;
        e.setCancelled(true);Player p=e.getPlayer();if(!p.hasPermission("conquest.world.admin")){selecting.remove(p.getUniqueId());return;}
        if(e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_BLOCK){selections.put(p.getUniqueId(),e.getClickedBlock().getLocation());p.sendMessage("First spawn corner selected. Right-click the opposite corner.");}
        else if(e.getAction()==org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK){
            Location a=selections.get(p.getUniqueId()),b=e.getClickedBlock().getLocation();
            if(a==null){p.sendMessage("Left-click the first corner first.");return;}
            if(!a.getWorld().equals(b.getWorld())||a.getWorld().getEnvironment()!=World.Environment.NORMAL){p.sendMessage("Both corners must be in the same overworld.");return;}
            if(module.data.getBoolean("spawn-border.active")){p.sendMessage("Use /spawn border off before changing the area.");return;}
            Object oldA=module.data.get("spawn-area.first"),oldB=module.data.get("spawn-area.second");
            module.data.set("spawn-area.first",a);module.data.set("spawn-area.second",b);
            if(!module.save()){module.data.set("spawn-area.first",oldA);module.data.set("spawn-area.second",oldB);p.sendMessage("Could not save selection.");return;}
            selecting.remove(p.getUniqueId());selections.remove(p.getUniqueId());p.sendMessage("Full-height spawn area saved. Building and PvP are blocked inside; combat-tagged players cannot enter.");
        }
    }
    private void deny(Player p){long now=System.currentTimeMillis();if(now-notices.getOrDefault(p.getUniqueId(),0L)>2000){notices.put(p.getUniqueId(),now);p.sendMessage("You cannot enter spawn while in combat.");}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent e){
        if(e instanceof PlayerTeleportEvent||module.bypass(e.getPlayer()))return;
        SpawnRegion region=region();
        if(region==null||!barrier.eligible(e.getPlayer())||!SpawnBarrier.crosses(region,e.getFrom(),e.getTo()))return;
        var away=SpawnBarrier.outward(region,e.getFrom());
        Location retreat=SpawnBarrier.retreat(region,e.getFrom(),away);
        retreat.setYaw(e.getTo().getYaw());retreat.setPitch(e.getTo().getPitch());
        e.setTo(retreat);deny(e.getPlayer());barrier.bounce(e.getPlayer(),away);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){
        if(module.bypass(e.getPlayer()))return;
        if(inside(e.getTo())&&!inside(e.getFrom())&&plugin.combat().tagged(e.getPlayer())){e.setCancelled(true);deny(e.getPlayer());}
    }
    @EventHandler public void vehicle(VehicleMoveEvent e){
        SpawnRegion region=region();
        if(region==null||!SpawnBarrier.crosses(region,e.getFrom(),e.getTo())||!taggedPassenger(e.getVehicle()))return;
        var away=SpawnBarrier.outward(region,e.getFrom());
        Location retreat=SpawnBarrier.retreat(region,e.getFrom(),away);
        eject(e.getVehicle(),retreat,away);
        e.getVehicle().teleport(retreat);e.getVehicle().setVelocity(new org.bukkit.util.Vector());
    }
    private void eject(Entity vehicle,Location retreat,org.bukkit.util.Vector away){
        for(Entity passenger:new ArrayList<>(vehicle.getPassengers())){
            eject(passenger,retreat,away);vehicle.removePassenger(passenger);passenger.teleport(retreat);
            if(passenger instanceof Player player&&barrier.eligible(player)){deny(player);barrier.bounce(player,away);}
        }
    }
    private boolean taggedPassenger(Entity entity){for(Entity p:entity.getPassengers())if(p instanceof Player player&&!module.bypass(player)&&plugin.combat().tagged(player)||taggedPassenger(p))return true;return false;}
    private Player playerSource(Entity entity){
        if(entity instanceof Player p)return p;
        if(entity instanceof Projectile p&&p.getShooter() instanceof Player player)return player;
        if(entity instanceof TNTPrimed t&&t.getSource() instanceof Player p)return p;
        if(entity instanceof Tameable t&&t.getOwner() instanceof Player p)return p;
        if(entity instanceof AreaEffectCloud c&&c.getSource() instanceof Player p)return p;
        return null;
    }
    private boolean safeSource(Entity entity){
        Player source=playerSource(entity);
        return inside(entity.getLocation())||entity.getPersistentDataContainer().has(origin,PersistentDataType.BYTE)||source!=null&&inside(source.getLocation());
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void launch(ProjectileLaunchEvent e){
        Player source=playerSource(e.getEntity());if(inside(e.getEntity().getLocation())||source!=null&&inside(source.getLocation()))e.getEntity().getPersistentDataContainer().set(origin,PersistentDataType.BYTE,(byte)1);
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e){if(module.bypass(playerSource(e.getDamager())))return;
        if(e.getEntity() instanceof Player&&(inside(e.getEntity().getLocation())||safeSource(e.getDamager())))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void splash(PotionSplashEvent e){if(module.bypass(playerSource(e.getPotion())))return;
        for(LivingEntity entity:e.getAffectedEntities())if(entity instanceof Player&&(inside(entity.getLocation())||safeSource(e.getPotion())))e.setIntensity(entity,0);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void lingering(LingeringPotionSplashEvent e){if(safeSource(e.getEntity()))e.getAreaEffectCloud().getPersistentDataContainer().set(origin,PersistentDataType.BYTE,(byte)1);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void cloud(AreaEffectCloudApplyEvent e){if(module.bypass(playerSource(e.getEntity())))return;e.getAffectedEntities().removeIf(entity->entity instanceof Player&&(inside(entity.getLocation())||safeSource(e.getEntity())));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void combust(EntityCombustByEntityEvent e){if(module.bypass(playerSource(e.getCombuster())))return;if(e.getEntity() instanceof Player&&(inside(e.getEntity().getLocation())||safeSource(e.getCombuster())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void blockDamage(EntityDamageByBlockEvent e){if(e.getEntity() instanceof Player&&(inside(e.getEntity().getLocation())||e.getDamager()!=null&&inside(e.getDamager().getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void explosive(EntitySpawnEvent e){if(e.getEntity() instanceof TNTPrimed&&inside(e.getLocation()))e.getEntity().getPersistentDataContainer().set(origin,PersistentDataType.BYTE,(byte)1);}
    @EventHandler public void death(PlayerDeathEvent e){barrier.forget(e.getEntity());}
    @EventHandler public void worldChanged(PlayerChangedWorldEvent e){barrier.forget(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){barrier.forget(e.getPlayer());selecting.remove(e.getPlayer().getUniqueId());selections.remove(e.getPlayer().getUniqueId());notices.remove(e.getPlayer().getUniqueId());}
}

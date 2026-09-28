package dev.turtleroles.survival;

import dev.turtleroles.service.GameplayBypass;
import dev.turtleroles.TurtleRolesPlugin;
import dev.turtleroles.service.ClientCompatibility;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Vanilla slots remain in the player's Ender inventory; extra slots live in player PDC. */
public final class ExpandedEnderChests implements Listener,AutoCloseable {
    private final TurtleRolesPlugin plugin;
    private final NamespacedKey key=new NamespacedKey("conquestsmp","ender_extra_slots");
    private final Map<UUID,View> views=new HashMap<>();
    private static final class View implements InventoryHolder {
        final Player owner; Inventory inventory;
        View(Player owner){this.owner=owner;}
        @Override public Inventory getInventory(){return inventory;}
    }
    public static boolean isEnderStorage(Inventory inventory) {
        return inventory.getType()==InventoryType.ENDER_CHEST || inventory.getHolder() instanceof View;
    }
    public ExpandedEnderChests(TurtleRolesPlugin plugin){this.plugin=plugin;}
    public void start(){
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Objects.requireNonNull(plugin.getCommand("enderchest")).setExecutor((sender,command,label,args)->{
            if(sender instanceof Player player && GameplayBypass.allowed(plugin,player)){open(player);return true;}
            sender.sendMessage(Component.text("Ender-chest commands are disabled. Use a physical Ender chest outside combat.",NamedTextColor.RED));
            return true;
        });
    }
    private void open(Player player){
        if(!ClientCompatibility.authenticated(player))return;
        if(blocked(player))return;
        player.closeInventory();
        View view=new View(player);view.inventory=Bukkit.createInventory(view,54,Component.text("Ender Chest",NamedTextColor.LIGHT_PURPLE));
        for(int i=0;i<27;i++)view.inventory.setItem(i,player.getEnderChest().getItem(i));
        byte[] saved=player.getPersistentDataContainer().get(key,PersistentDataType.BYTE_ARRAY);
        if(saved!=null)try {
            ItemStack[] items=ItemStack.deserializeItemsFromBytes(saved);
            for(int i=0;i<Math.min(27,items.length);i++)view.inventory.setItem(i+27,items[i]);
        }catch(RuntimeException ex){
            player.sendMessage(Component.text("Your extra Ender storage could not be loaded. Ask an administrator; the saved data has been retained.",NamedTextColor.RED));
            plugin.getLogger().severe("Cannot open extra Ender storage for "+player.getUniqueId()+": "+ex.getMessage());return;
        }
        views.put(player.getUniqueId(),view);player.openInventory(view.inventory);
    }
    private void save(View view){
        ItemStack[] contents=view.inventory.getContents();
        byte[] extra=ItemStack.serializeItemsAsBytes(Arrays.copyOfRange(contents,27,54));
        view.owner.getPersistentDataContainer().set(key,PersistentDataType.BYTE_ARRAY,extra);
        view.owner.getEnderChest().setContents(Arrays.copyOfRange(contents,0,27));
        view.owner.saveData();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent event){
        if(event.getHand()!=EquipmentSlot.HAND || !event.getAction().isRightClick() || event.getClickedBlock()==null
                || event.getClickedBlock().getType()!=Material.ENDER_CHEST || event.useInteractedBlock()==Event.Result.DENY)return;
        if(event.getPlayer().isSneaking() && event.hasItem())return;
        if(!event.getClickedBlock().getRelative(0,1,0).getType().isOccluding()) {
            event.setCancelled(true);open(event.getPlayer());
        }
    }
    @EventHandler(priority=EventPriority.NORMAL,ignoreCancelled=true)
    public void command(PlayerCommandPreprocessEvent event){
        String name=event.getMessage().substring(1).trim().split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        if(name.contains(":"))name=name.substring(name.indexOf(':')+1);
        var resolved=Bukkit.getPluginCommand(name);
        if(!Set.of("ec","echest","enderchest","eec","eechest","eenderchest","endersee").contains(name)
                &&(resolved==null||!resolved.getName().equalsIgnoreCase("enderchest")))return;
        event.setCancelled(true);
        if(GameplayBypass.allowed(plugin,event.getPlayer())){open(event.getPlayer());return;}
        event.getPlayer().sendMessage(Component.text("Ender-chest commands are disabled. Use a physical Ender chest outside combat.",NamedTextColor.RED));
    }
    private boolean blocked(Player player) {
        if(GameplayBypass.allowed(plugin,player)||!plugin.combat().tagged(player))return false;
        player.sendMessage(Component.text("You cannot open Ender chests during combat.",NamedTextColor.RED));
        return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void opening(InventoryOpenEvent event) {
        if(event.getPlayer() instanceof Player player && isEnderStorage(event.getInventory()) && blocked(player))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void combatClick(InventoryClickEvent event) {
        if(event.getWhoClicked() instanceof Player player && isEnderStorage(event.getView().getTopInventory()) && !GameplayBypass.allowed(plugin,player) && plugin.combat().tagged(player))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void combatDrag(InventoryDragEvent event) {
        if(event.getWhoClicked() instanceof Player player && isEnderStorage(event.getView().getTopInventory()) && !GameplayBypass.allowed(plugin,player) && plugin.combat().tagged(player))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void combatHit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if(event.getFinalDamage()<=0)return;
        java.util.Set<Player> participants=new java.util.HashSet<>();
        if(event.getEntity() instanceof Player p)participants.add(p);
        if(event.getDamager() instanceof Player p)participants.add(p);
        if(event.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player p)participants.add(p);
        Bukkit.getScheduler().runTask(plugin,()->{
            for(Player player:participants)if(player.isOnline() && !GameplayBypass.allowed(plugin,player) && plugin.combat().tagged(player) && isEnderStorage(player.getOpenInventory().getTopInventory()))player.closeInventory();
        });
    }
    private void changed(Inventory inventory){
        if(!(inventory.getHolder() instanceof View view))return;
        Bukkit.getScheduler().runTask(plugin,()->{if(views.get(view.owner.getUniqueId())==view)save(view);});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void click(InventoryClickEvent event){changed(event.getView().getTopInventory());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void drag(InventoryDragEvent event){changed(event.getView().getTopInventory());}
    @EventHandler public void closeInventory(InventoryCloseEvent event){
        if(event.getInventory().getHolder() instanceof View view){save(view);views.remove(view.owner.getUniqueId());}
    }
    @EventHandler public void quit(PlayerQuitEvent event){View view=views.remove(event.getPlayer().getUniqueId());if(view!=null)save(view);}
    @Override public void close(){
        for(View view:new ArrayList<>(views.values())){save(view);view.owner.closeInventory();}
        views.clear();HandlerList.unregisterAll(this);
    }
}

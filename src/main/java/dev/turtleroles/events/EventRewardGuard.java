package dev.turtleroles.events;

import dev.turtleroles.service.GameplayBypass;
import dev.turtleroles.survival.ExpandedEnderChests;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Publicly contestable rewards cannot be deposited into storage. */
final class EventRewardGuard implements Listener {
    private final JavaPlugin plugin;
    EventRewardGuard(JavaPlugin plugin){this.plugin=plugin;}
    static boolean reward(ItemStack item) {
        if(item==null||item.getType().isAir())return false;
        if(item.getType()==Material.DRAGON_EGG)return true;
        if(!item.hasItemMeta())return false;
        var pdc=item.getItemMeta().getPersistentDataContainer();
        String kind=pdc.get(new NamespacedKey("shocksmp","event_item_kind"),PersistentDataType.STRING);
        return (kind!=null&&kind.startsWith("mythic:"))||pdc.has(new NamespacedKey("kingscrown","crownsmp_crown"),PersistentDataType.BYTE)
            ||pdc.has(new NamespacedKey("shocksmp","shock_mace"),PersistentDataType.BYTE)
            ||pdc.has(new NamespacedKey("shocksmp","event_reward_issued"),PersistentDataType.BYTE)
            ||pdc.has(new NamespacedKey("shocksmp","warlord_piece"),PersistentDataType.STRING)
            ||pdc.has(new NamespacedKey("shocksmp","warlord_blade"),PersistentDataType.BYTE)
            ||pdc.has(WarlordRelics.PIECE,PersistentDataType.STRING);
    }
    static boolean contains(ItemStack item){return contains(item,0);}
    private static boolean contains(ItemStack item,int depth) {
        if(reward(item))return true;
        if(item==null||!item.hasItemMeta())return false;
        if(depth>=16)return true; // Refuse abnormally deep nesting rather than allow a storage bypass.
        var meta=item.getItemMeta();
        if(meta instanceof BundleMeta bundle)for(ItemStack child:bundle.getItems())if(contains(child,depth+1))return true;
        if(meta instanceof BlockStateMeta block&&block.getBlockState() instanceof Container c)
            for(ItemStack child:c.getInventory().getContents())if(contains(child,depth+1))return true;
        return false;
    }
    private void denied(org.bukkit.entity.HumanEntity player) {
        player.sendMessage(Component.text("Mythic and event rewards cannot be stored in containers or bundles.",NamedTextColor.RED));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void click(InventoryClickEvent event) {
        if(GameplayBypass.allowed(plugin,event.getWhoClicked()))return;
        ItemStack cursor=event.getCursor(), current=event.getCurrentItem();
        // Bundle insertion works in the personal inventory too, in either cursor direction.
        boolean bundleInsert=event.isRightClick()&&((current!=null&&current.getItemMeta() instanceof BundleMeta&&contains(cursor))
                ||(cursor!=null&&cursor.getItemMeta() instanceof BundleMeta&&contains(current)));
        Inventory top=event.getView().getTopInventory();
        boolean topSlot=event.getClickedInventory()==top;
        // Armor slots in the player's own crafting view remain usable.
        if(top.getType()==InventoryType.CRAFTING&&event.getSlotType()==InventoryType.SlotType.ARMOR)topSlot=false;
        boolean shift=event.isShiftClick()&&event.getClickedInventory()==event.getView().getBottomInventory()
                &&top.getType()!=InventoryType.CRAFTING&&top.getType()!=InventoryType.CREATIVE&&contains(current);
        ItemStack hotbar=event.getHotbarButton()>=0?event.getWhoClicked().getInventory().getItem(event.getHotbarButton()):null;
        ItemStack offhand=event.getClick()==ClickType.SWAP_OFFHAND?event.getWhoClicked().getInventory().getItemInOffHand():null;
        boolean deposit=topSlot&&(contains(hotbar)||contains(offhand)||contains(cursor)&&
                (event.getAction()==InventoryAction.PLACE_ALL||event.getAction()==InventoryAction.PLACE_ONE
                ||event.getAction()==InventoryAction.PLACE_SOME||event.getAction()==InventoryAction.SWAP_WITH_CURSOR));
        if(bundleInsert||shift||deposit){event.setCancelled(true);denied(event.getWhoClicked());}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drag(InventoryDragEvent event) {
        if(GameplayBypass.allowed(plugin,event.getWhoClicked()))return;
        if(contains(event.getOldCursor())&&event.getRawSlots().stream().anyMatch(s->s>=0&&s<event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);denied(event.getWhoClicked());
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(InventoryMoveItemEvent event) {
        if(contains(event.getItem()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(InventoryPickupItemEvent event) {
        if(contains(event.getItem().getItemStack()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void insert(org.bukkit.event.player.PlayerInteractEvent event) {
        if(GameplayBypass.allowed(plugin,event.getPlayer()))return;
        if(!event.getAction().isRightClick()||event.getClickedBlock()==null||!contains(event.getItem()))return;
        Material type=event.getClickedBlock().getType();
        if(type==Material.CHISELED_BOOKSHELF||type==Material.DECORATED_POT) {
            event.setCancelled(true);denied(event.getPlayer());
        }
    }
    private void returnItem(Player player,ItemStack item) {
        player.getInventory().addItem(item).values().forEach(i->player.getWorld().dropItemNaturally(player.getLocation(),i));
        player.sendMessage(Component.text("An event reward was returned from your Ender chest.",NamedTextColor.YELLOW));
    }
    void evict(Player player,Inventory inventory) {
        if(GameplayBypass.allowed(plugin,player))return;
        for(int i=0;i<inventory.getSize();i++)if(contains(inventory.getItem(i))) {
            ItemStack item=inventory.getItem(i);inventory.setItem(i,null);returnItem(player,item);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void open(InventoryOpenEvent event) {
        if(event.getPlayer() instanceof Player player&&ExpandedEnderChests.isEnderStorage(event.getInventory()))evict(player,event.getInventory());
    }
    @EventHandler public void join(PlayerJoinEvent event){Bukkit.getScheduler().runTaskLater(plugin,()->{
        Player player=event.getPlayer();if(!player.isOnline()||GameplayBypass.allowed(plugin,player))return;evict(player,player.getEnderChest());
        NamespacedKey key=new NamespacedKey("conquestsmp","ender_extra_slots");
        byte[] bytes=player.getPersistentDataContainer().get(key,PersistentDataType.BYTE_ARRAY);if(bytes==null)return;
        try {
            ItemStack[] items=ItemStack.deserializeItemsFromBytes(bytes);List<ItemStack> returned=new ArrayList<>();
            for(int i=0;i<items.length;i++)if(contains(items[i])){returned.add(items[i]);items[i]=null;}
            if(!returned.isEmpty()){
                player.getPersistentDataContainer().set(key,PersistentDataType.BYTE_ARRAY,ItemStack.serializeItemsAsBytes(items));
                returned.forEach(i->returnItem(player,i));player.saveData();
            }
        }catch(RuntimeException ex){plugin.getLogger().warning("Could not check saved Ender rewards for "+player.getUniqueId()+": "+ex.getMessage());}
    },5);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void despawn(ItemDespawnEvent event){if(contains(event.getEntity().getItemStack()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent event) {
        if(event.getEntity() instanceof Item item&&contains(item.getItemStack())) {
            event.setCancelled(true);
            if(event.getCause()==EntityDamageEvent.DamageCause.VOID){announce(item);item.remove();}
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void wear(org.bukkit.event.player.PlayerItemDamageEvent event){if(reward(event.getItem()))event.setCancelled(true);}
    @EventHandler public void removed(EntityRemoveEvent event) {
        if(event.getEntity() instanceof Item item&&event.getCause()==EntityRemoveEvent.Cause.OUT_OF_WORLD&&contains(item.getItemStack()))
            announce(item);
    }
    private void announce(Item item) {
            Bukkit.broadcast(Component.text("An event reward ("+item.getItemStack().getType().name().toLowerCase(Locale.ROOT).replace('_',' ')
                    +") was destroyed in the void. An administrator must run its event again to replace it.",NamedTextColor.RED));
    }
}

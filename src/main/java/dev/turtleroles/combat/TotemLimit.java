package dev.turtleroles.combat;

import dev.turtleroles.service.GameplayBypass;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Limits accessible totems, including offhand and cursor, without deleting overflow. */
public final class TotemLimit implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID,Long> notices=new HashMap<>();
    public TotemLimit(JavaPlugin plugin){this.plugin=plugin;}
    static int amount(ItemStack item){return item!=null&&item.getType()==Material.TOTEM_OF_UNDYING?item.getAmount():0;}
    static int carried(Player p){int count=amount(p.getItemOnCursor());for(ItemStack item:p.getInventory().getContents())count+=amount(item);return count;}
    private boolean bypass(Player p){return GameplayBypass.allowed(plugin,p);}
    private void notice(Player p){long now=System.currentTimeMillis();if(now-notices.getOrDefault(p.getUniqueId(),0L)>1500){notices.put(p.getUniqueId(),now);p.sendMessage(net.kyori.adventure.text.Component.text("You can carry a maximum of 2 totems, including your offhand.",net.kyori.adventure.text.format.NamedTextColor.RED));}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){
        if(e.getEntity() instanceof Player p&&!bypass(p)&&amount(e.getItem().getItemStack())>0&&carried(p)+amount(e.getItem().getItemStack())-e.getRemaining()>2){e.setCancelled(true);notice(p);}
    }
    static int incoming(InventoryClickEvent e,Player p){
        if(e.getRawSlot()<0||e.getRawSlot()>=e.getView().getTopInventory().getSize())return 0;
        int current=amount(e.getCurrentItem()),cursor=amount(e.getCursor());
        return switch(e.getAction()){
            case MOVE_TO_OTHER_INVENTORY,PICKUP_ALL -> current;
            case PICKUP_HALF -> (current+1)/2;
            case PICKUP_ONE -> Math.min(1,current);
            case PICKUP_SOME -> current;
            case SWAP_WITH_CURSOR -> current-cursor;
            case HOTBAR_SWAP,HOTBAR_MOVE_AND_READD -> current-amount(e.getHotbarButton()<0?p.getInventory().getItemInOffHand():p.getInventory().getItem(e.getHotbarButton()));
            case COLLECT_TO_CURSOR -> {if(cursor==0)yield 0;int total=0;for(ItemStack item:e.getView().getTopInventory().getContents())total+=amount(item);yield total;}
            default -> 0;
        };
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||bypass(p))return;
        int added=incoming(e,p);
        if(e.getAction()==InventoryAction.COLLECT_TO_CURSOR&&amount(e.getCursor())>0){added=0;for(ItemStack item:e.getView().getTopInventory().getContents())added+=amount(item);}
        if(added>0&&carried(p)+added>2){e.setCancelled(true);notice(p);}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){plugin.getServer().getScheduler().runTask(plugin,()->enforce(e.getPlayer()));}
    public void enforce(Player p){
        if(!p.isOnline()||p.isDead()||bypass(p)||carried(p)<=2)return;
        int remaining=2;boolean overflow=false;
        // Preserve the equipped offhand first, then inventory slots, then cursor.
        List<Integer> slots=new ArrayList<>();slots.add(40);for(int i=0;i<40;i++)slots.add(i);
        for(int slot:slots){ItemStack item=p.getInventory().getItem(slot);int count=amount(item);if(count==0)continue;int keep=Math.min(count,remaining);remaining-=keep;
            if(count>keep){ItemStack extra=item.clone();extra.setAmount(count-keep);if(keep==0)p.getInventory().setItem(slot,null);else{item.setAmount(keep);p.getInventory().setItem(slot,item);}drop(p,extra);overflow=true;}}
        ItemStack cursor=p.getItemOnCursor();int count=amount(cursor);if(count>remaining){ItemStack extra=cursor.clone();extra.setAmount(count-remaining);if(remaining==0)p.setItemOnCursor(null);else{cursor.setAmount(remaining);p.setItemOnCursor(cursor);}drop(p,extra);overflow=true;}
        if(overflow)notice(p);
    }
    private void drop(Player p,ItemStack item){var dropped=p.getWorld().dropItemNaturally(p.getLocation(),item);dropped.setPickupDelay(40);}
    @EventHandler public void quit(org.bukkit.event.player.PlayerQuitEvent e){notices.remove(e.getPlayer().getUniqueId());}
}

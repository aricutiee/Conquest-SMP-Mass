package dev.turtleroles.events;

import dev.turtleroles.service.GameplayBypass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.function.Predicate;

/** Keep existing possessions intact, but only the actual designated boss may equip or make netherite armor. */
final class NetheriteArmorRule implements Listener,AutoCloseable {
    private final Predicate<Player> boss;
    private final JavaPlugin plugin;
    private final Map<UUID,Long> notices=new HashMap<>();
    private final BukkitTask task;
    NetheriteArmorRule(JavaPlugin plugin,Predicate<Player> boss){
        this.plugin=plugin;this.boss=p->boss.test(p)||GameplayBypass.allowed(plugin,p);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        task=Bukkit.getScheduler().runTaskTimer(plugin,()->Bukkit.getOnlinePlayers().forEach(this::enforce),1,5);
    }
    static boolean armor(ItemStack item){return item!=null&&Set.of(Material.NETHERITE_HELMET,Material.NETHERITE_CHESTPLATE,Material.NETHERITE_LEGGINGS,Material.NETHERITE_BOOTS).contains(item.getType());}
    private void message(Player player,boolean crafting){
        long now=System.currentTimeMillis();if(now-notices.getOrDefault(player.getUniqueId(),0L)<1000)return;
        notices.put(player.getUniqueId(),now);
        player.sendMessage(Component.text(crafting?"You are not allowed to craft netherite armor.":"Only the active Warlord and Juggernaut may wear netherite armor.",NamedTextColor.RED));
    }
    private static boolean upgrade(SmithingInventory inv){
        ItemStack base=inv.getItem(1),addition=inv.getItem(2);
        return base!=null&&addition!=null&&addition.getType()==Material.NETHERITE_INGOT
            &&Set.of(Material.DIAMOND_HELMET,Material.DIAMOND_CHESTPLATE,Material.DIAMOND_LEGGINGS,Material.DIAMOND_BOOTS).contains(base.getType());
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void prepare(PrepareSmithingEvent event){
        if(event.getView().getPlayer() instanceof Player p&&!boss.test(p)&&(armor(event.getResult())||upgrade(event.getInventory())))event.setResult(null);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void prepareCraft(PrepareItemCraftEvent event){
        if(event.getView().getPlayer() instanceof Player p&&!boss.test(p)&&armor(event.getInventory().getResult()))event.getInventory().setResult(null);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void click(InventoryClickEvent event){
        if(!(event.getWhoClicked() instanceof Player p)||boss.test(p))return;
        boolean crafting=event.getSlotType()==InventoryType.SlotType.RESULT&&
            (armor(event.getCurrentItem())||event.getInventory() instanceof SmithingInventory smith&&upgrade(smith)
             ||event instanceof CraftItemEvent craft&&armor(craft.getRecipe().getResult())
             ||event.getInventory() instanceof CraftingInventory craftingInventory&&craftingInventory.getRecipe()!=null&&armor(craftingInventory.getRecipe().getResult()));
        if(crafting){event.setCancelled(true);message(p,true);return;}
        ItemStack hotbar=event.getHotbarButton()>=0?p.getInventory().getItem(event.getHotbarButton()):null;
        boolean equip=event.getSlotType()==InventoryType.SlotType.ARMOR&&(armor(event.getCursor())||armor(hotbar))
            ||event.isShiftClick()&&event.getView().getTopInventory().getType()==InventoryType.CRAFTING&&armor(event.getCurrentItem());
        if(equip){event.setCancelled(true);message(p,false);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drag(InventoryDragEvent event){
        if(event.getWhoClicked() instanceof Player p&&!boss.test(p)&&event.getView().getTopInventory().getType()==InventoryType.CRAFTING
            &&armor(event.getOldCursor())&&event.getRawSlots().stream().anyMatch(s->s>=5&&s<=8)){event.setCancelled(true);message(p,false);}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void use(PlayerInteractEvent event){
        if(!boss.test(event.getPlayer())&&event.getAction().isRightClick()&&armor(event.getItem())){event.setUseItemInHand(Event.Result.DENY);message(event.getPlayer(),false);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void dispense(BlockDispenseArmorEvent event){
        if(event.getTargetEntity() instanceof Player p&&!boss.test(p)&&armor(event.getItem()))event.setCancelled(true);
    }
    void enforce(Player player){
        if(player.isDead()||boss.test(player))return;
        ItemStack[] equipment=player.getInventory().getArmorContents();List<ItemStack> returned=new ArrayList<>();
        for(int i=0;i<equipment.length;i++)if(armor(equipment[i])){returned.add(equipment[i]);equipment[i]=null;}
        if(returned.isEmpty())return;
        player.getInventory().setArmorContents(equipment);
        for(ItemStack item:returned)player.getInventory().addItem(item).values().forEach(extra->player.getWorld().dropItemNaturally(player.getLocation(),extra));
        message(player,false);
    }
    @EventHandler public void quit(PlayerQuitEvent event){notices.remove(event.getPlayer().getUniqueId());}
    public void close(){task.cancel();HandlerList.unregisterAll(this);notices.clear();}
}

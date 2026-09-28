package dev.turtleroles.service;

import org.bukkit.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.Objects;

public final class CustomItemNames implements Listener {
    private final org.bukkit.plugin.Plugin plugin;
    public CustomItemNames(org.bukkit.plugin.Plugin plugin){this.plugin=plugin;}
    public static boolean custom(ItemStack item) {
        if(item==null || item.getType().isAir())return false;
        if(item.getType()==Material.DRAGON_EGG)return true;
        if(!item.hasItemMeta())return false;
        var meta=item.getItemMeta();
        return !meta.getPersistentDataContainer().isEmpty() || meta.hasCustomModelData() || meta.hasItemModel();
    }
    public static boolean renamed(ItemStack input,ItemStack result) {
        if(!custom(input) || result==null)return false;
        var before=input.getItemMeta().displayName();var after=result.getItemMeta().displayName();
        if(before==null || after==null)return !Objects.equals(before,after);
        var plain=PlainTextComponentSerializer.plainText();
        return !plain.serialize(before).equals(plain.serialize(after));
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareAnvilEvent e) {
        if(GameplayBypass.allowed(plugin,e.getView().getPlayer()))return;
        ItemStack input=e.getInventory().getItem(0),result=e.getResult();
        if(renamed(input,result)) {e.setResult(null);return;}
        if(custom(input) && result!=null) {
            var meta=result.getItemMeta();meta.displayName(input.getItemMeta().displayName());result.setItemMeta(meta);e.setResult(result);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void take(InventoryClickEvent e) {
        if(GameplayBypass.allowed(plugin,e.getWhoClicked()))return;
        if(e.getView().getTopInventory().getType()!=InventoryType.ANVIL || e.getRawSlot()!=2)return;
        if(renamed(e.getView().getTopInventory().getItem(0),e.getCurrentItem())) {
            e.setCancelled(true);e.getWhoClicked().sendMessage("You cannot rename custom items in an anvil.");
        }
    }
}

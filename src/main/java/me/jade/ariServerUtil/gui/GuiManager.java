package me.jade.ariServerUtil.gui;

import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.util.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class GuiManager implements Listener {
    private final JavaPlugin plugin;
    private final ServerUtilConfig config;

    public GuiManager(JavaPlugin plugin, ServerUtilConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public Inventory create(Player viewer, String key, String title, int size, Consumer<GuiClick> clickHandler) {
        return create(viewer, key, title, size, clickHandler, null);
    }

    public Inventory create(Player viewer, String key, String title, int size, Consumer<GuiClick> clickHandler, Consumer<GuiClose> closeHandler) {
        GuiHolder holder = new GuiHolder(viewer.getUniqueId(), key, clickHandler, closeHandler);
        Inventory inventory = Bukkit.createInventory(holder, size, PlainTextComponentSerializer.plainText().serialize(Text.mm(title)));
        holder.setInventory(inventory);
        return inventory;
    }

    public ItemStack button(Material material, String name, List<String> lore, boolean allowed) {
        ItemStack item = new ItemStack(allowed ? material : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm((allowed ? "<green>" : "<red>") + name));
        List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Text.mm(line));
        }
        lines.add(Text.mm(allowed ? "<gray>Click to open." : "<red>You do not have permission."));
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack plain(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name));
        meta.lore(lore.stream().map(Text::mm).toList());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (event.getClick().isShiftClick()
                || event.getClick() == ClickType.DOUBLE_CLICK
                || event.getClick() == ClickType.NUMBER_KEY
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getAction() == InventoryAction.HOTBAR_SWAP
                || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
            return;
        }
        holder.click(new GuiClick(event, event.getRawSlot()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            for (int slot : event.getRawSlots()) {
                if (slot < event.getView().getTopInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof GuiHolder holder) {
            holder.close(new GuiClose(event));
        }
    }
}

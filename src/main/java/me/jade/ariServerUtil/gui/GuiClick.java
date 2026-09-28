package me.jade.ariServerUtil.gui;

import org.bukkit.event.inventory.InventoryClickEvent;

public record GuiClick(InventoryClickEvent event, int slot) {
}

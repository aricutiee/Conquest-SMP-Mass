package me.jade.ariServerUtil.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;
import java.util.function.Consumer;

public final class GuiHolder implements InventoryHolder {
    private Inventory inventory;
    private final UUID viewer;
    private final String key;
    private final Consumer<GuiClick> clickHandler;
    private final Consumer<GuiClose> closeHandler;

    public GuiHolder(UUID viewer, String key, Consumer<GuiClick> clickHandler, Consumer<GuiClose> closeHandler) {
        this.viewer = viewer;
        this.key = key;
        this.clickHandler = clickHandler;
        this.closeHandler = closeHandler;
    }

    public UUID viewer() {
        return viewer;
    }

    public String key() {
        return key;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void click(GuiClick click) {
        clickHandler.accept(click);
    }

    public void close(GuiClose close) {
        if (closeHandler != null) {
            closeHandler.accept(close);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

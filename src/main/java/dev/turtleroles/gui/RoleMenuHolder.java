package dev.turtleroles.gui;

import dev.turtleroles.role.Role;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public record RoleMenuHolder(UUID actorUuid, UUID targetUuid, int targetRevision, Role requestedRole, Stage stage) implements InventoryHolder {
    public enum Stage {
        LIST,
        CONFIRM
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

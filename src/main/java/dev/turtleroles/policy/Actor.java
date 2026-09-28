package dev.turtleroles.policy;

import dev.turtleroles.role.Role;

import java.util.Optional;
import java.util.UUID;

public record Actor(UUID uuid, String name, Role role, boolean console, boolean ownerOverride) {
    public static Actor systemConsole() {
        return new Actor(null, "CONSOLE", Role.OWNER, true, true);
    }

    public static Actor player(UUID uuid, String name, Role role) {
        return new Actor(uuid, name, role, false, role == Role.OWNER);
    }

    public static Actor player(UUID uuid, String name, Role role, boolean operatorOverride) {
        return new Actor(uuid, name, operatorOverride ? Role.OWNER : role, false, operatorOverride || role == Role.OWNER);
    }

    public Optional<UUID> optionalUuid() {
        return Optional.ofNullable(uuid);
    }
}

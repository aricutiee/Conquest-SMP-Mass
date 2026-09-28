package dev.turtleroles.storage;

import dev.turtleroles.role.Role;

import java.time.Instant;
import java.util.UUID;

public record PlayerRecord(UUID uuid, String lastName, String lowerName, Role role, int revision, Instant updatedAt) {
}

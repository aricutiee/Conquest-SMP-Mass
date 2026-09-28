package dev.turtleroles;

import dev.turtleroles.pack.PackStatus;
import dev.turtleroles.pack.PlayerPackState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackStateTest {
    @Test
    void loadedStateIsBoundToCurrentConnectionAndRevision() {
        UUID connection = UUID.randomUUID();
        PlayerPackState state = new PlayerPackState(connection, "abc", PackStatus.LOADED, 1);
        assertTrue(state.readyFor(connection, "abc"));
        assertFalse(state.readyFor(UUID.randomUUID(), "abc"));
        assertFalse(state.readyFor(connection, "def"));
        assertFalse(new PlayerPackState(connection, "abc", PackStatus.DECLINED, 1).readyFor(connection, "abc"));
    }
}

package dev.turtleroles;

import dev.turtleroles.role.Role;
import dev.turtleroles.service.RoleService;
import dev.turtleroles.storage.PlayerRepository;
import dev.turtleroles.storage.SQLiteDatabase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class OperatorRoleTest {
    @TempDir
    Path tempDir;

    @Test
    void directOpHasOwnerLevelWithoutChangingStoredRole() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (SQLiteDatabase database = new SQLiteDatabase(tempDir.resolve("operators.db"))) {
            database.open();
            PlayerRepository players = new PlayerRepository(database);
            players.upsertKnownPlayer(uuid, "Operator");
            RoleService roles = new RoleService(mock(Plugin.class), players);
            Player player = mock(Player.class);
            when(player.isOp()).thenReturn(true, false);
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
                assertEquals(Role.OWNER, roles.effectiveRoleOf(uuid));
                assertEquals(Role.MEMBER, players.findByUuid(uuid).orElseThrow().role());
                assertEquals(Role.MEMBER, roles.effectiveRoleOf(uuid));
            }
        }
    }
}

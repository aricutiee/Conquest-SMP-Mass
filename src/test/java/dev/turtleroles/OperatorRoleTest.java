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

    @Test void boosterBenefitSurvivesReloadAndCanBeRemoved()throws Exception{
        UUID id=UUID.randomUUID();try(SQLiteDatabase db=new SQLiteDatabase(tempDir.resolve("benefits.db"))){
            db.open();var repo=new PlayerRepository(db);repo.upsertKnownPlayer(id,"DiamondPlayer");
            Plugin plugin=mock(Plugin.class);when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
            var service=new RoleService(plugin,repo);service.setBooster(id,2);
            var reload=new RoleService(plugin,repo);assertEquals(2,reload.boosterTier(id));assertEquals(Role.MEMBER,reload.roleOf(id));
            reload.setBooster(id,0);assertEquals(0,new RoleService(plugin,repo).boosterTier(id));
        }
    }
    @Test
    void directOpCannotBecomeOwner() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (SQLiteDatabase database = new SQLiteDatabase(tempDir.resolve("operators.db"))) {
            database.open();
            PlayerRepository players = new PlayerRepository(database);
            players.upsertKnownPlayer(uuid, "Operator");
            Plugin plugin=mock(Plugin.class);when(plugin.getDataFolder()).thenReturn(tempDir.toFile());RoleService roles = new RoleService(plugin, players);
            Player player = mock(Player.class);
            when(player.isOp()).thenReturn(true, false);
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
                assertEquals(Role.MEMBER, roles.effectiveRoleOf(uuid));
                assertEquals(Role.MEMBER, players.findByUuid(uuid).orElseThrow().role());
                assertEquals(Role.MEMBER, roles.effectiveRoleOf(uuid));
            }
        }
    }
}

package dev.turtleroles;

import dev.turtleroles.policy.Actor;
import dev.turtleroles.punishment.PunishmentCase;
import dev.turtleroles.punishment.PunishmentType;
import dev.turtleroles.role.Role;
import dev.turtleroles.storage.PlayerRecord;
import dev.turtleroles.storage.PlayerRepository;
import dev.turtleroles.storage.PunishmentRepository;
import dev.turtleroles.storage.SQLiteDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsRolesAcrossReconnectAndNameChange() throws Exception {
        Path dbPath = tempDir.resolve("roles.db");
        UUID playerId = UUID.randomUUID();
        try (SQLiteDatabase database = open(dbPath)) {
            PlayerRepository players = new PlayerRepository(database);
            PlayerRecord first = players.upsertKnownPlayer(playerId, "FirstName");
            assertEquals(Role.MEMBER, first.role());
            players.setRole(first.uuid(), Role.ADMIN, Actor.systemConsole(), "test");
            PlayerRecord renamed = players.upsertKnownPlayer(playerId, "NewName");
            assertEquals(Role.ADMIN, renamed.role());
            assertEquals("NewName", renamed.lastName());
        }
        try (SQLiteDatabase database = open(dbPath)) {
            PlayerRepository players = new PlayerRepository(database);
            assertEquals(Role.ADMIN, players.findByUuid(playerId).orElseThrow().role());
            assertEquals("NewName", players.findByName("newname").orElseThrow().lastName());
        }
    }

    @Test
    void sameNameWithNewUuidKeepsOldRolesAttachedToOldUuid() throws Exception {
        try (SQLiteDatabase database = open(tempDir.resolve("uuid-change.db"))) {
            PlayerRepository players = new PlayerRepository(database);
            UUID offlineId = UUID.randomUUID();
            UUID verifiedId = UUID.randomUUID();
            players.upsertKnownPlayer(offlineId, "Nirvantia");
            players.setRole(offlineId, Role.ADMIN, Actor.systemConsole(), "existing role");

            PlayerRecord verified = players.upsertKnownPlayer(verifiedId, "Nirvantia");

            assertEquals(Role.MEMBER, verified.role());
            assertEquals(verifiedId, players.findByName("nirvantia").orElseThrow().uuid());
            assertEquals(Role.ADMIN, players.findByUuid(offlineId).orElseThrow().role());
            assertEquals("~" + offlineId, players.findByUuid(offlineId).orElseThrow().lowerName());
            assertEquals(2, players.listPlayers().size());
        }
    }

    @Test
    void ownerBootstrapAndTransferAreDurable() throws Exception {
        try (SQLiteDatabase database = open(tempDir.resolve("owner.db"))) {
            PlayerRepository players = new PlayerRepository(database);
            PlayerRecord oldOwner = players.upsertKnownPlayer(UUID.randomUUID(), "Owner");
            PlayerRecord newOwner = players.upsertKnownPlayer(UUID.randomUUID(), "Next");
            players.bootstrapOwner(oldOwner, Actor.systemConsole());
            assertThrows(Exception.class, () -> players.bootstrapOwner(newOwner, Actor.systemConsole()));
            int revision = players.ownershipRevision();
            players.transferOwner(players.findByUuid(oldOwner.uuid()).orElseThrow(), newOwner, Actor.systemConsole(), revision);
            assertEquals(Role.CO_OWNER, players.findByUuid(oldOwner.uuid()).orElseThrow().role());
            assertEquals(Role.OWNER, players.findByUuid(newOwner.uuid()).orElseThrow().role());
            assertEquals(1, players.ownerCount());
        }
    }

    @Test
    void multipleOwnersCanBeGrantedWithoutDemotingExistingOwners() throws Exception {
        Path dbPath = tempDir.resolve("multiple-owners.db");
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        try (SQLiteDatabase database = open(dbPath)) {
            PlayerRepository players = new PlayerRepository(database);
            players.upsertKnownPlayer(firstId, "FirstOwner");
            players.upsertKnownPlayer(secondId, "SecondOwner");
            players.setRole(firstId, Role.OWNER, Actor.systemConsole(), "first");
            players.setRole(secondId, Role.OWNER, Actor.systemConsole(), "second");
            assertEquals(2, players.ownerCount());
        }
        try (SQLiteDatabase database = open(dbPath)) {
            PlayerRepository players = new PlayerRepository(database);
            assertEquals(Role.OWNER, players.findByUuid(firstId).orElseThrow().role());
            assertEquals(Role.OWNER, players.findByUuid(secondId).orElseThrow().role());
        }
    }

    @Test
    void punishmentExpiryAndRevocationSurviveRestart() throws Exception {
        Path dbPath = tempDir.resolve("punishments.db");
        UUID targetId = UUID.randomUUID();
        long caseId;
        try (SQLiteDatabase database = open(dbPath)) {
            PlayerRepository players = new PlayerRepository(database);
            PunishmentRepository punishments = new PunishmentRepository(database);
            PlayerRecord target = players.upsertKnownPlayer(targetId, "Target");
            PunishmentCase mute = punishments.create(PunishmentType.TEMP_MUTE, target, Actor.systemConsole(), "test mute", Instant.now().plusSeconds(60));
            caseId = mute.id();
            assertTrue(punishments.activeMute(targetId, Instant.now()).isPresent());
        }
        try (SQLiteDatabase database = open(dbPath)) {
            PlayerRepository players = new PlayerRepository(database);
            PunishmentRepository punishments = new PunishmentRepository(database);
            PlayerRecord target = players.findByUuid(targetId).orElseThrow();
            assertTrue(punishments.activeMute(target.uuid(), Instant.now()).isPresent());
            punishments.revoke(caseId, Actor.systemConsole(), "appeal");
            assertFalse(punishments.activeMute(target.uuid(), Instant.now()).isPresent());
            assertTrue(punishments.get(caseId).orElseThrow().revoked());
        }
    }

    @Test
    void failedRoleMutationDoesNotCreateUnknownPlayer() throws Exception {
        try (SQLiteDatabase database = open(tempDir.resolve("rollback.db"))) {
            PlayerRepository players = new PlayerRepository(database);
            UUID unknown = UUID.randomUUID();
            assertThrows(Exception.class, () -> players.setRole(unknown, Role.ADMIN, Actor.systemConsole(), "fail"));
            assertTrue(players.findByUuid(unknown).isEmpty());
        }
    }

    private SQLiteDatabase open(Path path) throws Exception {
        SQLiteDatabase database = new SQLiteDatabase(path);
        database.open();
        return database;
    }
}

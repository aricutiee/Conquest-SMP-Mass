package dev.turtleroles.events;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EggSpawnLedgerTest {
    @Test void newExplorationAreaCanSpawnAfterOldChunkIsExhausted() {
        EggSpawnLedger ledger = new EggSpawnLedger();
        var old = new EggSpawnLedger.Spot("world", 0, 0);
        var newArea = new EggSpawnLedger.Spot("world", 1000, 1000);
        for (int i = 0; i < 4; i++) {
            UUID id = UUID.randomUUID();
            ledger.placed(id, old);
            assertTrue(ledger.collected(id));
        }
        assertFalse(ledger.canPlace(old, 48, 4, 8));
        assertTrue(ledger.canPlace(newArea, 48, 4, 8));
        assertEquals(4, ledger.collectionHistory().get(EggSpawnLedger.chunk(old)));
    }

    @Test void overlappingPlayersShareOneDensityBudget() {
        EggSpawnLedger ledger = new EggSpawnLedger();
        UUID egg = UUID.randomUUID();
        ledger.placed(egg, new EggSpawnLedger.Spot("world", 24, 16));
        assertEquals(1, ledger.near("world", 0, 0, 48));
        assertEquals(1, ledger.near("world", 40, 0, 48));
        assertFalse(ledger.canPlace(new EggSpawnLedger.Spot("world", 25, 17), 48, 4, 8));
        assertFalse(ledger.collected(UUID.randomUUID()));
        assertTrue(ledger.collected(egg));
        assertFalse(ledger.collected(egg));
    }

    @Test void retirementFreesGlobalBudgetButDoesNotResetChunkHistory() {
        EggSpawnLedger ledger = new EggSpawnLedger();
        UUID egg = UUID.randomUUID();
        var old = new EggSpawnLedger.Spot("world", 0, 0);
        ledger.placed(egg, old);
        assertFalse(ledger.canPlace(new EggSpawnLedger.Spot("world", 1000, 1000), 1, 1, 8));
        ledger.retired(egg);
        assertTrue(ledger.canPlace(new EggSpawnLedger.Spot("world", 1000, 1000), 1, 1, 8));
        assertFalse(ledger.canPlace(old, 1, 1, 8));
    }

    @Test void worldAndNegativeChunkCoordinatesAreDistinct() {
        var overworld = new EggSpawnLedger.Spot("world", -1, -1);
        var nether = new EggSpawnLedger.Spot("world_nether", -1, -1);
        assertEquals("world:-1:-1", EggSpawnLedger.chunk(overworld));
        assertNotEquals(EggSpawnLedger.chunk(overworld), EggSpawnLedger.chunk(nether));
    }
}

package dev.turtleroles.migration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class LegacyDataMigrationTest {
    @TempDir Path root;
    @Test void copiesDataAndLeavesOriginalIntact() throws Exception {
        Path old = root.resolve("shockSMP"); Files.createDirectories(old.resolve("nested"));
        Files.writeString(old.resolve("turtleroles.db"), "database");
        Files.writeString(old.resolve("nested/events.yml"), "saved event");
        Path next = root.resolve("ConquestSMP");
        assertTrue(LegacyDataMigration.copy(next));
        assertEquals("database", Files.readString(next.resolve("turtleroles.db")));
        assertEquals("saved event", Files.readString(next.resolve("nested/events.yml")));
        assertEquals("database", Files.readString(old.resolve("turtleroles.db")));
        Files.writeString(next.resolve("turtleroles.db"), "new state");
        assertFalse(LegacyDataMigration.copy(next));
        assertEquals("new state", Files.readString(next.resolve("turtleroles.db")));
    }
    @Test void freshInstallNeedsNoLegacyData() throws Exception {
        assertFalse(LegacyDataMigration.copy(root.resolve("ConquestSMP")));
    }
}

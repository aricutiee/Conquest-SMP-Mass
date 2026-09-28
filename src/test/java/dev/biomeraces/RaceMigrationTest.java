package dev.biomeraces;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RaceMigrationTest {
    @TempDir Path directory;
    @Test void migrationCopiesSavedRacesAndNeverOverwritesNewerData() throws Exception {
        Path old = directory.resolve("old.yml"), current = directory.resolve("new.yml");
        Files.writeString(old, "original choices and cooldowns");
        RaceModule.copyIfAbsent(old, current);
        assertEquals("original choices and cooldowns", Files.readString(current));
        assertEquals("original choices and cooldowns", Files.readString(old));
        Files.writeString(current, "newer choices"); RaceModule.copyIfAbsent(old, current);
        assertEquals("newer choices", Files.readString(current));
        assertFalse(Files.exists(directory.resolve("new.yml.migration")));
    }
}

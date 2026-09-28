package me.jade.ariServerUtil.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ModuleFilesTest {
    @TempDir Path folder;

    @Test
    void preservesExistingConfigurationAndDatabase() throws Exception {
        Files.writeString(folder.resolve("config.yml"), "existing settings");
        Files.writeString(folder.resolve("serverutil.db"), "existing database");
        ModuleFiles.installDefaults(folder, name -> new ByteArrayInputStream(name.getBytes(StandardCharsets.UTF_8)));
        assertEquals("existing settings", Files.readString(folder.resolve("config.yml")));
        assertEquals("existing database", Files.readString(folder.resolve("serverutil.db")));
        assertEquals("serverutil/messages.yml", Files.readString(folder.resolve("messages.yml")));
        ModuleFiles.installDefaults(folder, name -> { throw new AssertionError("Existing files must not be replaced"); });
    }

    @Test
    void freshInstallationUsesUtilityDefaultsRatherThanRolesDefaults() throws Exception {
        Path target = folder.resolve("ServerUtil");
        ModuleFiles.installDefaults(target, name -> new ByteArrayInputStream(name.getBytes(StandardCharsets.UTF_8)));
        assertEquals("serverutil/config.yml", Files.readString(target.resolve("config.yml")));
        assertEquals("serverutil/messages.yml", Files.readString(target.resolve("messages.yml")));
    }
}

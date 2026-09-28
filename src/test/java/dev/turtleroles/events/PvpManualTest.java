package dev.turtleroles.events;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class PvpManualTest {
    ServerMock server;JavaPlugin plugin;
    @BeforeEach void setup(){server=MockBukkit.mock();plugin=MockBukkit.createMockPlugin();}
    @AfterEach void teardown(){MockBukkit.unmock();}
    @Test void oldAutomaticStateIsRetiredAndCannotStartItself() throws Exception {
        File file=new File(plugin.getDataFolder(),"pvp-hour-state.yml");
        YamlConfiguration old=new YamlConfiguration();old.set("automatic",true);old.set("next-start",1);
        old.set("ends-at",System.currentTimeMillis()+3600_000);old.save(file);
        var settings=new YamlConfiguration();settings.set("pvp-hour.automatic",true);
        var pvp=new PvpHour(plugin,settings);
        assertFalse(pvp.active());server.getScheduler().performTicks(400);assertFalse(pvp.active());
        assertTrue(pvp.status().contains("manual starts only"));
        assertFalse(YamlConfiguration.loadConfiguration(file).getBoolean("automatic"));
        assertTrue(pvp.start());pvp.shutdown();
        var restored=new PvpHour(plugin,settings);assertTrue(restored.active());assertTrue(restored.stop());restored.shutdown();
    }
}

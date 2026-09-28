package dev.biomeraces;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RaceRollTest {
    ServerMock server; RaceModule plugin; PlayerMock player;
    @BeforeEach void setup() {
        server = MockBukkit.mock(); plugin = RaceTestHost.load().races;
        player = new CombatIntegrationTest.DryPlayer(server, "Roller"); server.addPlayer(player);
    }
    @AfterEach void cleanup() { MockBukkit.unmock(); }
    @Test void fiveSecondScheduleStrictlySlowsAndEndsAt100Ticks() {
        RollSettings settings = new RollSettings(plugin.settings().yaml);
        assertEquals(100, settings.duration); int[] frames = settings.frameTicks();
        assertEquals(24, frames.length); assertEquals(0, frames[0]);
        int previous = 0;
        for (int i = 0; i < frames.length; i++) {
            int hold = (i + 1 < frames.length ? frames[i + 1] : settings.duration) - frames[i];
            assertTrue(hold >= previous && hold > 0); previous = hold;
        }
        assertTrue(previous > 10);
    }
    @Test void firstJoinSavesDrawBeforeAnimationAndAwardsOnlyAtFiveSeconds() throws Exception {
        assertNull(plugin.state(player).base);
        server.getScheduler().performTicks(20);
        assertTrue(plugin.rolls().running(player.getUniqueId()));
        Race draw = plugin.state(player).pendingRoll; assertNotNull(draw); assertNotEquals(Race.DRAGONBORN, draw);
        PlayerStore disk = new PlayerStore(plugin.getDataFolder().toPath().resolve("race-players.yml"));
        assertEquals(draw, disk.get(player.getUniqueId()).pendingRoll); assertNull(disk.get(player.getUniqueId()).base);
        server.getScheduler().performTicks(99); assertNull(plugin.state(player).base);
        server.getScheduler().performOneTick(); assertEquals(draw, plugin.state(player).base);
        assertFalse(plugin.rolls().running(player.getUniqueId())); assertNull(plugin.state(player).pendingRoll);
        disk = new PlayerStore(plugin.getDataFolder().toPath().resolve("race-players.yml"));
        assertEquals(draw, disk.get(player.getUniqueId()).base); assertNull(disk.get(player.getUniqueId()).pendingRoll);
    }
    @Test void interruptedRollReusesPersistedOutcomeAcrossRejoinAndModuleRestart() {
        server.getScheduler().performTicks(45); Race draw = plugin.state(player).pendingRoll;
        server.getPluginManager().callEvent(new PlayerQuitEvent(player, Component.empty()));
        assertFalse(plugin.rolls().running(player.getUniqueId())); assertEquals(draw, plugin.state(player).pendingRoll);
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));
        server.getScheduler().performTicks(30); assertEquals(draw, plugin.state(player).pendingRoll);
        RaceTestHost host = (RaceTestHost) plugin.host(); plugin.close();
        host.races = new RaceModule(host); plugin = host.races; assertTrue(plugin.enable());
        assertEquals(draw, plugin.state(player).pendingRoll);
        server.getScheduler().performTicks(120); assertEquals(draw, plugin.state(player).base);
    }
    @Test void adminPreviewIsRepeatablePrivateAndDoesNotMutateRaceOrCooldowns() {
        plugin.state(player).base = Race.DWARF;
        plugin.state(player).offenseUntil = 9999999999999L;
        plugin.state(player).defenseUntil = 9999999999998L;
        player.setOp(false); player.performCommand("roll"); assertFalse(plugin.rolls().running(player.getUniqueId()));
        player.setOp(true); player.performCommand("roll"); assertTrue(plugin.rolls().running(player.getUniqueId()));
        player.performCommand("roll"); // Duplicate command must not restart the clock.
        server.getScheduler().performTicks(100);
        assertFalse(plugin.rolls().running(player.getUniqueId())); assertEquals(Race.DWARF, plugin.state(player).base);
        assertNull(plugin.state(player).pendingRoll);
        assertEquals(9999999999999L, plugin.state(player).offenseUntil); assertEquals(9999999999998L, plugin.state(player).defenseUntil);
        player.performCommand("roll"); assertTrue(plugin.rolls().running(player.getUniqueId()));
    }
    @Test void adminSetCancelsPendingAwardAndResetRetainsCooldowns() {
        server.getScheduler().performTicks(40); assertNotNull(plugin.state(player).pendingRoll);
        player.setOp(true); player.performCommand("race set Roller petalfolk");
        assertFalse(plugin.rolls().running(player.getUniqueId())); assertNull(plugin.state(player).pendingRoll);
        server.getScheduler().performTicks(110); assertEquals(Race.PETALFOLK, plugin.state(player).base);
        plugin.state(player).defenseUntil = 9999999999999L; player.performCommand("race reset Roller");
        server.getScheduler().performTicks(120); assertNotNull(plugin.state(player).base);
        assertEquals(9999999999999L, plugin.state(player).defenseUntil);
    }
    @Test void defaultsAreEqualWeightedAndDragonbornNeverDrawn() {
        Random random = new Random(987654); RollSettings settings = new RollSettings(plugin.settings().yaml);
        Map<Race, Integer> counts = new EnumMap<>(Race.class);
        for (int i = 0; i < 50000; i++) counts.merge(settings.choose(random), 1, Integer::sum);
        assertEquals(new HashSet<>(RollSettings.BASE), counts.keySet());
        for (int count : counts.values()) assertTrue(count > 9400 && count < 10600);
        for (Race race : RollSettings.BASE) plugin.settings().yaml.set("roll.weights." + race.key(), race == Race.DWARF ? 1 : 0);
        settings = new RollSettings(plugin.settings().yaml);
        for (int i = 0; i < 100; i++) assertEquals(Race.DWARF, settings.choose(random));
    }
    @Test void existingRaceDoesNotRollAgainOnLogin() {
        plugin.state(player).base = Race.ROOTBOUND;
        server.getScheduler().performTicks(150);
        assertFalse(plugin.rolls().running(player.getUniqueId())); assertNull(plugin.state(player).pendingRoll);
        assertEquals(Race.ROOTBOUND, plugin.state(player).base);
    }
    @Test void deathPausesPendingDrawAndRespawnReplaysTheSameOutcome() {
        server.getScheduler().performTicks(40); Race draw = plugin.state(player).pendingRoll;
        player.setHealth(0); server.getScheduler().performTicks(150);
        assertFalse(plugin.rolls().running(player.getUniqueId())); assertNull(plugin.state(player).base);
        assertEquals(draw, plugin.state(player).pendingRoll);
        player.respawn(); server.getScheduler().performTicks(120);
        assertEquals(draw, plugin.state(player).base); assertNull(plugin.state(player).pendingRoll);
    }
    @Test void eachOf24NameChangesHasPrivateNoteAndFinalRevealHasDragonSound() {
        plugin.state(player).base = Race.DWARF; player.setOp(true);
        PlayerMock bystander = new CombatIntegrationTest.DryPlayer(server, "Bystander"); server.addPlayer(bystander);
        plugin.state(bystander).base = Race.DWARF;
        player.performCommand("roll"); server.getScheduler().performTicks(100);
        var sounds = player.getHeardSounds(); assertEquals(25, sounds.size());
        for (int i = 0; i < 24; i++) assertEquals("minecraft:block.note_block.harp", sounds.get(i).getSound());
        assertEquals("minecraft:entity.ender_dragon.growl", sounds.get(24).getSound());
        assertTrue(bystander.getHeardSounds().isEmpty());
        assertTrue(sounds.get(0).getPitch() > sounds.get(23).getPitch());
    }
    @Test void invalidReloadRetainsSettingsAndDoesNotCancelRunningRoll() throws Exception {
        server.getScheduler().performTicks(40); Race draw = plugin.state(player).pendingRoll;
        var previous = plugin.settings();
        java.nio.file.Files.writeString(plugin.getDataFolder().toPath().resolve("races.yml"), "roll:\n  duration-seconds: -1\n");
        assertThrows(IllegalArgumentException.class, () -> plugin.reloadSettings());
        assertSame(previous, plugin.settings()); assertTrue(plugin.rolls().running(player.getUniqueId()));
        server.getScheduler().performTicks(80); assertEquals(draw, plugin.state(player).base);
    }
}

package me.jade.ariServerUtil.grace;

import me.jade.ariServerUtil.announcements.AnnouncementService;
import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.persistence.Database;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Criteria;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GracePeriodServiceTest {
    @Test void startsWithAnnouncementEndsOnceAndRejectsNonPositiveDuration() throws Exception {
        try (var bukkit = mockStatic(Bukkit.class)) {
            JavaPlugin plugin = mock(JavaPlugin.class);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            AtomicReference<Runnable> timer = new AtomicReference<>();
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            doAnswer(call -> { timer.set(call.getArgument(1)); return null; })
                    .when(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong());
            ServerUtilConfig config = mock(ServerUtilConfig.class);
            when(config.string(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
            when(config.bool(anyString(), anyBoolean())).thenAnswer(call -> call.getArgument(1));
            when(config.message(anyString())).thenReturn("Grace status");
            Database database = mock(Database.class);
            when(database.query(any())).thenReturn(CompletableFuture.completedFuture("0"));
            AnnouncementService announcements = mock(AnnouncementService.class);
            GracePeriodService service = new GracePeriodService(plugin, config, database, mock(AuditService.class), announcements);
            Player staff = mock(Player.class);
            service.start(staff, Duration.ZERO, true);
            assertFalse(service.active());
            verifyNoInteractions(announcements);
            service.start(staff, Duration.ofMinutes(1), false);
            assertTrue(service.active());
            verify(announcements).broadcast(contains("STARTED"), contains("PvP is disabled"));
            var end = GracePeriodService.class.getDeclaredField("endTime");
            end.setAccessible(true);
            end.set(service, Instant.now().minusSeconds(1));
            timer.get().run();
            timer.get().run();
            assertFalse(service.active());
            verify(announcements, times(1)).broadcast(contains("ENDED"), contains("PvP is now enabled"));
            service.close();
        }
    }

    @Test void protectsAndRestoresPlayerNeedsAcrossDamageHungerAndQuit() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            JavaPlugin plugin = mock(JavaPlugin.class);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            Criteria criteria = mock(Criteria.class);
            bukkit.when(() -> Bukkit.getScoreboardCriteria("dummy")).thenReturn(criteria);

            Player protectedPlayer = mock(Player.class);
            Player attacker = mock(Player.class);
            UUID protectedId = UUID.randomUUID();
            when(protectedPlayer.getUniqueId()).thenReturn(protectedId);
            when(protectedPlayer.getFoodLevel()).thenReturn(11);
            when(protectedPlayer.getSaturation()).thenReturn(3.5f);
            when(protectedPlayer.getExhaustion()).thenReturn(2.0f);
            Scoreboard scoreboard = mock(Scoreboard.class);
            Objective objective = mock(Objective.class);
            Score score = mock(Score.class);
            when(protectedPlayer.getScoreboard()).thenReturn(scoreboard);
            when(scoreboard.registerNewObjective(anyString(), any(Criteria.class), any(net.kyori.adventure.text.Component.class)))
                    .thenReturn(objective);
            when(objective.getScore(anyString())).thenReturn(score);
            World world = mock(World.class);
            UUID worldId = UUID.randomUUID();
            when(world.getUID()).thenReturn(worldId);
            when(world.getDifficulty()).thenReturn(Difficulty.NORMAL, Difficulty.NORMAL, Difficulty.PEACEFUL);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(protectedPlayer));

            ServerUtilConfig config = mock(ServerUtilConfig.class);
            when(config.string(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
            when(config.message(anyString())).thenReturn("Grace status");
            when(config.bool(anyString(), anyBoolean())).thenAnswer(call -> call.getArgument(1));
            Database database = mock(Database.class);
            when(database.query(any())).thenReturn(CompletableFuture.completedFuture("0"));
            GracePeriodService service = new GracePeriodService(plugin, config, database,
                    mock(AuditService.class), mock(AnnouncementService.class));

            service.start(mock(Player.class), Duration.ofMinutes(2), false);
            verify(protectedPlayer).setFoodLevel(20);
            verify(protectedPlayer).setSaturation(20.0f);
            verify(protectedPlayer).setExhaustion(0.0f);
            verify(world).setDifficulty(Difficulty.PEACEFUL);

            EntityDamageEvent fall = mock(EntityDamageEvent.class);
            when(fall.getEntity()).thenReturn(protectedPlayer);
            service.onPlayerDamage(fall);
            verify(fall).setCancelled(true);

            EntityDamageByEntityEvent pvp = mock(EntityDamageByEntityEvent.class);
            when(pvp.getEntity()).thenReturn(protectedPlayer);
            when(pvp.getDamager()).thenReturn(attacker);
            service.onDamage(pvp);
            verify(pvp).setCancelled(true);

            FoodLevelChangeEvent hunger = mock(FoodLevelChangeEvent.class);
            when(hunger.getEntity()).thenReturn(protectedPlayer);
            service.onHunger(hunger);
            verify(hunger).setCancelled(true);

            PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(protectedPlayer);
            service.onQuit(quit);
            verify(protectedPlayer).setFoodLevel(11);
            verify(protectedPlayer).setSaturation(3.5f);
            verify(protectedPlayer).setExhaustion(2.0f);
            service.close();
            verify(world).setDifficulty(Difficulty.NORMAL);
        }
    }
}

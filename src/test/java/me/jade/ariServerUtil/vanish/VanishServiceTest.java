package me.jade.ariServerUtil.vanish;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.persistence.Database;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;

class VanishServiceTest {
    @Test void joiningPlayersAreHiddenImmediatelyAndAgainNextTick() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            JavaPlugin plugin = mock(JavaPlugin.class);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            List<Runnable> pending = new ArrayList<>();
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(call -> { pending.add(call.getArgument(1)); return null; }).when(scheduler).runTask(eq(plugin), any(Runnable.class));
            Player admin = mock(Player.class), joining = mock(Player.class);
            UUID id = UUID.randomUUID();
            when(admin.getUniqueId()).thenReturn(id);
            when(admin.getName()).thenReturn("Admin");
            when(admin.isOp()).thenReturn(true);
            when(joining.getUniqueId()).thenReturn(UUID.randomUUID());
            when(joining.hasPermission(anyString())).thenReturn(true);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(admin, joining));
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(admin);
            Database database = mock(Database.class);
            when(database.query(any())).thenReturn(CompletableFuture.completedFuture(null));
            ServerUtilConfig config = mock(ServerUtilConfig.class);
            when(config.message(anyString())).thenReturn("<player>");
            VanishService service = new VanishService(plugin, config, database, mock(AuditService.class));
            service.vanish(admin);
            clearInvocations(joining);
            PlayerJoinEvent event = mock(PlayerJoinEvent.class);
            when(event.getPlayer()).thenReturn(joining);
            service.onJoin(event);
            verify(joining).hidePlayer(plugin, admin);
            verify(joining).unlistPlayer(admin);
            pending.forEach(Runnable::run);
            verify(joining, times(2)).unlistPlayer(admin);
            when(event.getPlayer()).thenReturn(admin);
            service.onJoin(event);
            verify(event).joinMessage(null);
            verify(joining, never()).listPlayer(admin);
        }
    }
}

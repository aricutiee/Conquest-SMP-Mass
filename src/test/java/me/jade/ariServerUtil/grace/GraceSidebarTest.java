package me.jade.ariServerUtil.grace;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.Component;
import java.util.List;
import static org.mockito.Mockito.*;

class GraceSidebarTest {
    @Test void sharedScoreboardUpdatesOnceAndRestoresPreviousSidebarWithoutReplacingTeams() {
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
        bukkit.when(() -> org.bukkit.Bukkit.getScoreboardCriteria(anyString())).thenAnswer(invocation -> new Criteria() {
            public String getName() { return invocation.getArgument(0); }
            public boolean isReadOnly() { return false; }
            public RenderType getDefaultRenderType() { return RenderType.INTEGER; }
        });
        Scoreboard board = mock(Scoreboard.class);
        Objective old = mock(Objective.class), grace = mock(Objective.class);
        when(old.getScoreboard()).thenReturn(board);
        when(grace.getScoreboard()).thenReturn(board);
        when(board.getObjective(DisplaySlot.SIDEBAR)).thenReturn(old);
        when(board.registerNewObjective(anyString(), any(Criteria.class), any(Component.class))).thenReturn(grace);
        Score sixty = mock(Score.class), fiftyNine = mock(Score.class), header = mock(Score.class);
        when(grace.getScore("§aPvP protection active")).thenReturn(header);
        when(grace.getScore("§fTime left: §e01:00")).thenReturn(sixty);
        when(grace.getScore("§fTime left: §e00:59")).thenReturn(fiftyNine);
        Player a = mock(Player.class), b = mock(Player.class);
        when(a.getScoreboard()).thenReturn(board);
        when(b.getScoreboard()).thenReturn(board);
        GraceSidebar sidebar = new GraceSidebar();
        sidebar.update(List.of(a, b), 60);
        sidebar.update(List.of(a, b), 59);
        verify(board, times(1)).registerNewObjective(anyString(), any(Criteria.class), any(Component.class));
        verify(sixty).resetScore();
        verify(fiftyNine, times(1)).setScore(1);
        verify(a, never()).setScoreboard(any());
        when(board.getObjective(DisplaySlot.SIDEBAR)).thenReturn(grace);
        sidebar.close();
        verify(grace).unregister();
        verify(old).setDisplaySlot(DisplaySlot.SIDEBAR);
        sidebar.close();
        verify(grace, times(1)).unregister();
        }
    }
}

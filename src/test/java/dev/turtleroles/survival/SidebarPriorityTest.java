package dev.turtleroles.survival;
import org.junit.jupiter.api.Test;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import java.util.UUID;
import static org.mockito.Mockito.*;
class SidebarPriorityTest {
    @Test void joiningPlayerKeepsAnAlreadyVisibleEventSidebar() {
        var player=mock(Player.class);var board=mock(Scoreboard.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getScoreboard()).thenReturn(board);
        when(board.getObjective(DisplaySlot.SIDEBAR)).thenReturn(mock(Objective.class));
        new SurvivalSidebar(new NamespacedKey("conquestsmp","test_streak"),p->false).refresh(player);
        verify(player,never()).setScoreboard(any());
    }
}

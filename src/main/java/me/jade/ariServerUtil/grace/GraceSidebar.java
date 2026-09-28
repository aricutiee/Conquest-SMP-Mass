package me.jade.ariServerUtil.grace;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.*;

/** Adds a temporary objective to existing boards without replacing their teams. */
public final class GraceSidebar implements AutoCloseable {
    private final Map<Scoreboard, BoardState> boards = new IdentityHashMap<>();

    public void update(Collection<? extends Player> players, long seconds) {
        Set<Scoreboard> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Player player : players) {
            Scoreboard board = player.getScoreboard();
            if (!seen.add(board)) continue;
            BoardState state = boards.computeIfAbsent(board, this::create);
            String line = "§fTime left: §e" + GraceCountdown.format(seconds);
            if (!line.equals(state.line)) {
                if (state.line != null) state.objective.getScore(state.line).resetScore();
                state.objective.getScore(line).setScore(1);
                state.line = line;
            }
        }
    }

    private BoardState create(Scoreboard board) {
        Objective previous = board.getObjective(DisplaySlot.SIDEBAR);
        String name = "su_grace";
        int suffix = 0;
        while (board.getObjective(name) != null) name = "su_grace" + ++suffix;
        Objective objective = board.registerNewObjective(name, org.bukkit.Bukkit.getScoreboardCriteria("dummy"),
                Component.text("GRACE PERIOD", NamedTextColor.GREEN));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.getScore("§aPvP protection active").setScore(2);
        return new BoardState(objective, previous);
    }

    @Override
    public void close() {
        boards.forEach((board, state) -> {
            if (state.objective.getScoreboard() == null) return;
            boolean stillDisplayed = board.getObjective(DisplaySlot.SIDEBAR) == state.objective;
            state.objective.unregister();
            if (stillDisplayed && state.previous != null && state.previous.getScoreboard() != null) {
                state.previous.setDisplaySlot(DisplaySlot.SIDEBAR);
            }
        });
        boards.clear();
    }

    private static final class BoardState {
        final Objective objective;
        final Objective previous;
        String line;
        BoardState(Objective objective, Objective previous) { this.objective = objective; this.previous = previous; }
    }
}

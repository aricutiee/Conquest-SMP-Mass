package me.jade.ariServerUtil.vanish;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class VanishVisibility {
    private VanishVisibility() { }

    public static void apply(Plugin plugin, Player viewer, Player subject, boolean vanished) {
        if (viewer.getUniqueId().equals(subject.getUniqueId())) return;
        // Explicit server policy: a permission grant alone never reveals admins to non-OP players.
        if (vanished && !viewer.isOp()) {
            viewer.hidePlayer(plugin, subject);
            viewer.unlistPlayer(subject);
        } else {
            viewer.showPlayer(plugin, subject);
            // Respect visibility restrictions owned by any other plugin.
            if (viewer.canSee(subject)) viewer.listPlayer(subject);
        }
    }
}

package me.jade.ariServerUtil.announcements;

import me.jade.ariServerUtil.audit.AuditService;
import me.jade.ariServerUtil.config.ServerUtilConfig;
import me.jade.ariServerUtil.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class AnnouncementService {
    private final ServerUtilConfig config;
    private final AuditService audit;

    public AnnouncementService(ServerUtilConfig config, AuditService audit) {
        this.config = config;
        this.audit = audit;
    }

    public void announce(Player staff, String message) {
        if (message == null || message.isBlank()) {
            Text.send(staff, config.message("announcement.empty"));
            return;
        }
        String title = config.string("announcement.title", "<red><message>").replace("<message>", message);
        String subtitle = config.string("announcement.subtitle", "<gray>Announcement from <staff>").replace("<staff>", staff.getName()).replace("<message>", message);
        broadcast(title, subtitle);
        audit.record(staff, "ANNOUNCEMENT", null, "", message, "sent", "");
    }

    /** Shared title timing and sound for administration and grace announcements. */
    public void broadcast(String title, String subtitle) {
        int fadeIn = config.integer("announcement.fade-in-ticks", 10, 0, 200);
        int stay = config.integer("announcement.stay-ticks", 70, 20, 1200);
        int fadeOut = config.integer("announcement.fade-out-ticks", 20, 0, 200);
        Sound sound = config.sound("announcement.sound", Sound.ENTITY_ENDER_DRAGON_GROWL);
        float volume = (float) config.config().getDouble("announcement.volume", 1.0);
        float pitch = (float) config.config().getDouble("announcement.pitch", 1.0);
        Title adventureTitle = Title.title(Text.mm(title), Text.mm(subtitle), Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L)));
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.showTitle(adventureTitle);
            player.playSound(player.getLocation(), sound, volume, pitch);
        });
    }
}

package dev.turtleroles.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public record TurtleConfig(
    Duration helperMuteLimit,
    Duration moderatorMuteLimit,
    Duration moderatorBanLimit,
    List<String> mutedCommandAliases,
    int reasonMaxLength,
    int historyPageSize,
    int invseeRefreshTicks,
    ResourcePackSettings resourcePack,
    boolean overheadTagsEnabled,
    boolean strictOpReconciliation
) {
    public static TurtleConfig from(FileConfiguration config) {
        return new TurtleConfig(
            Duration.ofSeconds(config.getLong("limits.helper-temp-mute-seconds", 3600)),
            Duration.ofSeconds(config.getLong("limits.moderator-temp-mute-seconds", 604800)),
            Duration.ofSeconds(config.getLong("limits.moderator-temp-ban-seconds", 604800)),
            config.getStringList("mute.blocked-command-aliases"),
            config.getInt("limits.reason-max-length", 240),
            config.getInt("history.page-size", 8),
            config.getInt("inventory-inspection.refresh-ticks", 10),
            ResourcePackSettings.from(config),
            config.getBoolean("presentation.overhead-tags-enabled", true),
            config.getBoolean("security.strict-op-reconciliation", true)
        );
    }

    public record ResourcePackSettings(
        boolean enabled,
        boolean autoSendOnJoin,
        boolean required,
        String url,
        String sha1,
        UUID uuid,
        int timeoutSeconds,
        int maxRetries,
        String prompt,
        boolean selfHostEnabled,
        int selfHostPort
    ) {
        static ResourcePackSettings from(FileConfiguration config) {
            String uuidText = config.getString("resource-pack.uuid", "6d68e47c-1680-478f-bd80-f54ef82dfef0");
            return new ResourcePackSettings(
                config.getBoolean("resource-pack.enabled", true),
                config.getBoolean("resource-pack.auto-send-on-join", true),
                config.getBoolean("resource-pack.required", true),
                config.getString("resource-pack.url", ""),
                config.getString("resource-pack.sha1", ""),
                UUID.fromString(uuidText),
                config.getInt("resource-pack.timeout-seconds", 45),
                config.getInt("resource-pack.max-retries", 1),
                config.getString("resource-pack.prompt", "TurtleRoles uses this server pack to show role badges in chat and the player list. Accept it to join."),
                config.getBoolean("resource-pack.self-host.enabled", false),
                config.getInt("resource-pack.self-host.port", 2053)
            );
        }
    }
}

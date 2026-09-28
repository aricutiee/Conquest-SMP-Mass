package me.jade.ariServerUtil.config;

import me.jade.ariServerUtil.util.DurationParser;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

public final class ServerUtilConfig {
    private final JavaPlugin plugin;
    private FileConfiguration messages;
    private FileConfiguration settings;

    public ServerUtilConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            me.jade.ariServerUtil.config.ModuleFiles.installDefaults(dataFolder().toPath(), plugin::getResource);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Could not initialize ServerUtil configuration", ex);
        }
        settings = YamlConfiguration.loadConfiguration(new File(dataFolder(), "config.yml"));
        messages = YamlConfiguration.loadConfiguration(new File(dataFolder(), "messages.yml"));
    }

    public File dataFolder() {
        return new File(plugin.getDataFolder().getParentFile(), "ServerUtil");
    }
    public FileConfiguration config() {
        return settings;
    }

    public String message(String path) {
        return messages.getString(path, "<red>Missing message: " + path);
    }

    public String string(String path, String fallback) {
        return settings.getString(path, fallback);
    }

    public boolean bool(String path, boolean fallback) {
        return settings.getBoolean(path, fallback);
    }

    public int integer(String path, int fallback, int min, int max) {
        int value = settings.getInt(path, fallback);
        if (value < min || value > max) {
            plugin.getLogger().warning(path + " is outside " + min + ".." + max + ". Using " + fallback + ".");
            return fallback;
        }
        return value;
    }

    public Duration duration(String path, String fallback) {
        String value = settings.getString(path, fallback);
        try {
            return DurationParser.parse(value);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning(path + " has invalid duration '" + value + "'. Using " + fallback + ".");
            return DurationParser.parse(fallback);
        }
    }

    public Material material(String path, Material fallback) {
        String value = settings.getString(path, fallback.name());
        Material material = Material.matchMaterial(value == null ? "" : value.toUpperCase(Locale.ROOT));
        if (material == null) {
            plugin.getLogger().warning(path + " has invalid material '" + value + "'. Using " + fallback + ".");
            return fallback;
        }
        return material;
    }

    public Sound sound(String path, Sound fallback) {
        String value = settings.getString(path, fallback.name());
        try {
            return Sound.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning(path + " has invalid sound '" + value + "'. Using " + fallback + ".");
            return fallback;
        }
    }

    public List<String> list(String path) {
        return settings.getStringList(path);
    }
}

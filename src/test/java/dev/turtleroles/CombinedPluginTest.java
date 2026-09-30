package dev.turtleroles;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CombinedPluginTest {
    @Test
    @SuppressWarnings("unchecked")
    void allCommandsHaveOneUnambiguousOwner() {
        Map<String, Object> descriptor = new Yaml().load(getClass().getResourceAsStream("/plugin.yml"));
        assertEquals("ConquestSMP", descriptor.get("name"));
        assertEquals(TurtleRolesPlugin.class.getName(), descriptor.get("main"));
        Map<String, Map<String, Object>> commands = (Map<String, Map<String, Object>>) descriptor.get("commands");
        assertEquals(Set.of("troll", "fakeplayers", "staff", "string", "npc", "afk", "shards", "setword", "nick", "leaderboard", "rtp", "smp", "kit", "enderchest", "home", "homes", "sethome", "delhome", "spawn", "worldspawn", "setworldspawn", "dimensions", "conquestac", "race", "roll", "events", "event", "warlord", "warlordevent", "juggernaut", "role", "tr", "warn", "warnings", "unwarn", "tempmute", "mute", "unmute", "tempban", "ban", "unban", "kick", "history", "case", "gamemode", "invsee", "util", "report", "reports", "staffmode", "staffchat", "sc"), commands.keySet());
        Set<String> names = new HashSet<>(commands.keySet());
        commands.values().forEach(spec -> {
            for (String alias : (List<String>) spec.getOrDefault("aliases", List.of())) {
                assertTrue(names.add(alias), "Command or alias collision: " + alias);
            }
        });
        Map<String, Object> permissions = (Map<String, Object>) descriptor.get("permissions");
        assertTrue(permissions.containsKey("serverutil.use"));
        assertTrue(permissions.containsKey("turtleroles.owner"));
        assertEquals("op", permissions.get("shocksmp.events.admin") instanceof Map<?, ?> value ? value.get("default") : null);
        assertEquals("op", permissions.get("shocksmp.events.kit") instanceof Map<?, ?> value ? value.get("default") : null);
    }

    @Test
    void onePluginLifecycleIncludesBothCodebasesAndResources() {
        assertTrue(me.jade.ariServerUtil.AriServerUtil.class.isAssignableFrom(TurtleRolesPlugin.class));
        for (String resource : List.of("/config.yml", "/races.yml", "/events.yml", "/messages.yml", "/roles.yml", "/serverutil/config.yml", "/serverutil/messages.yml", "/generated-resource-pack/ConquestSMP-resource-pack.zip")) {
            assertNotNull(getClass().getResource(resource), "Missing " + resource);
        }
    }
}


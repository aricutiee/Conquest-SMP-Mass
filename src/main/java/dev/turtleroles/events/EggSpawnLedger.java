package dev.turtleroles.events;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One event-wide placement ledger shared by overlapping player exploration areas. */
public final class EggSpawnLedger {
    public record Spot(String world, int x, int z) {}
    private final Map<UUID, Spot> active = new LinkedHashMap<>();
    private final Map<String, Integer> placed = new HashMap<>();
    private final Map<String, Integer> collected = new HashMap<>();

    public boolean canPlace(Spot spot, int globalLimit, int perChunkLimit, int minimumSpacing) {
        if (active.size() >= globalLimit || placed.getOrDefault(chunk(spot), 0) >= perChunkLimit) return false;
        for (Spot other : active.values()) if (other.world().equals(spot.world())
                && distanceSquared(spot, other) < minimumSpacing * minimumSpacing) return false;
        return true;
    }

    public void placed(UUID id, Spot spot) {
        if (active.putIfAbsent(id, spot) != null) throw new IllegalStateException("Egg already recorded");
        placed.merge(chunk(spot), 1, Integer::sum);
    }

    public boolean collected(UUID id) {
        Spot spot = active.remove(id);
        if (spot == null) return false;
        collected.merge(chunk(spot), 1, Integer::sum);
        return true;
    }

    public void retired(UUID id) { active.remove(id); }

    public int near(String world, int x, int z, int radius) {
        Spot center = new Spot(world, x, z);
        int count = 0;
        for (Spot spot : active.values()) if (world.equals(spot.world())
                && distanceSquared(center, spot) <= radius * radius) count++;
        return count;
    }

    public int activeCount() { return active.size(); }
    public Map<String, Integer> placementHistory() { return Map.copyOf(placed); }
    public Map<String, Integer> collectionHistory() { return Map.copyOf(collected); }
    public void clear() { active.clear(); placed.clear(); collected.clear(); }

    public static String chunk(Spot spot) { return spot.world() + ":" + (spot.x() >> 4) + ":" + (spot.z() >> 4); }
    private static int distanceSquared(Spot a, Spot b) {
        int dx = a.x() - b.x(), dz = a.z() - b.z();
        return dx * dx + dz * dz;
    }
}

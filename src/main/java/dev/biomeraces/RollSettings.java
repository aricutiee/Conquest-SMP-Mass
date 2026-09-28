package dev.biomeraces;

import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.format.TextColor;
import java.util.*;
import java.util.random.RandomGenerator;

/** Validated weighted draw and a strictly increasing, progressively slowing tick schedule. */
public final class RollSettings {
    public static final List<Race> BASE = List.of(Race.BOGBORN, Race.ROOTBOUND, Race.PETALFOLK, Race.HOLLOW_EYED, Race.DWARF);
    public final int duration, frames, joinDelay;
    private final double total;
    private final Map<Race, Double> weights = new EnumMap<>(Race.class);
    private final Map<Race, TextColor> colors = new EnumMap<>(Race.class);
    public RollSettings(YamlConfiguration yaml) {
        double seconds = yaml.getDouble("roll.duration-seconds", 5);
        if (!Double.isFinite(seconds) || seconds < 1 || seconds > 30) throw new IllegalArgumentException("Roll duration must be 1..30 seconds");
        duration = (int) Math.round(seconds * 20);
        frames = yaml.getInt("roll.frames", 24); joinDelay = yaml.getInt("roll.join-delay-ticks", 20);
        if (frames < 2 || frames > duration || joinDelay < 0 || joinDelay > 1200) throw new IllegalArgumentException("Invalid roll frames or join delay");
        double sum = 0;
        String[] fallback = {"#60c98b", "#c19a6b", "#ffa7cf", "#d8e3dd", "#f2aa50"};
        for (int i = 0; i < BASE.size(); i++) {
            Race race = BASE.get(i); double weight = yaml.getDouble("roll.weights." + race.key(), 1);
            if (!Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Invalid race weight: " + race);
            weights.put(race, weight); sum += weight;
            TextColor color = TextColor.fromHexString(yaml.getString("roll.colors." + race.key(), fallback[i]));
            if (color == null) throw new IllegalArgumentException("Race roll colors must be #RRGGBB: " + race);
            colors.put(race, color);
        }
        if (!Double.isFinite(sum) || sum <= 0) throw new IllegalArgumentException("At least one base race must have a positive weight");
        total = sum;
        for (String sound : List.of("tick", "reveal")) {
            String id = yaml.getString("roll." + sound + "-sound", "");
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid roll sound identifier: " + id);
            double volume = yaml.getDouble("roll." + sound + "-volume");
            if (!Double.isFinite(volume) || volume < 0 || volume > 2) throw new IllegalArgumentException("Roll sound volume must be 0..2");
        }
        for (String path : List.of("tick-pitch-start", "tick-pitch-end", "reveal-pitch")) {
            double pitch = yaml.getDouble("roll." + path, 1);
            if (!Double.isFinite(pitch) || pitch < .5 || pitch > 2) throw new IllegalArgumentException("Roll pitch must be 0.5..2: " + path);
        }
    }
    public Race choose(RandomGenerator random) {
        double draw = random.nextDouble(total);
        Race last = null;
        for (Race race : BASE) {
            double weight = weights.get(race);
            if (weight <= 0) continue;
            last = race; draw -= weight; if (draw < 0) return race;
        }
        return Objects.requireNonNull(last);
    }
    public TextColor color(Race race) { return colors.get(race); }
    public int[] frameTicks() {
        // Reserve one tick for every frame, then distribute the remaining time with quartic easing.
        double sum = 0;
        for (int i = 0; i < frames; i++) sum += Math.pow((i + 1d) / frames, 4);
        int[] holds = new int[frames]; int allocated = 0;
        for (int i = 0; i < frames; i++) {
            holds[i] = 1 + (int) Math.floor((duration - frames) * Math.pow((i + 1d) / frames, 4) / sum);
            allocated += holds[i];
        }
        // Put rounding leftovers at the slow end; hold lengths never decrease.
        for (int i = frames - (duration - allocated); i < frames; i++) holds[i]++;
        int[] result = new int[frames];
        for (int i = 1; i < frames; i++) result[i] = result[i - 1] + holds[i - 1];
        return result;
    }
}

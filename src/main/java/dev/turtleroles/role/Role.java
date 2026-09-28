package dev.turtleroles.role;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Role {
    OWNER("owner", "OWNER", 700, "\uE001", "owner.png", new String[]{"#5BCEFA", "#F5A9B8", "#FFFFFF", "#F5A9B8", "#5BCEFA"}),
    CO_OWNER("co_owner", "CO-OWNER", 600, "\uE002", "co_owner.png", new String[]{"#164B35", "#3E9161", "#A8CA7B"}),
    SR_ADMIN("sr_admin", "SR ADMIN", 500, "\uE003", "sr_admin.png", new String[]{"#080A0D", "#242830", "#8E98A6"}),
    ADMIN("admin", "ADMIN", 400, "\uE004", "admin.png", new String[]{"#650E24", "#8B1234", "#BD2142"}),
    SSER("sser", "SSER", 350, "\uE00A", "sser.png", new String[]{"#5BCEFA", "#F5A9B8", "#FFFFFF", "#F5A9B8", "#5BCEFA"}),
    MODERATOR("moderator", "MODERATOR", 300, "\uE005", "moderator.png", new String[]{"#14532D", "#15803D", "#41CE70"}),
    HELPER("helper", "HELPER", 200, "\uE006", "helper.png", new String[]{"#A96D09", "#D49A12", "#FFE477"}),
    MEDIA("media", "MEDIA", 150, "\uE009", "media.png", new String[]{"#BD188F", "#FF45C5", "#FFA6E8"}),
    BOOSTER_X2("booster_x2", "BOOSTER X2", 130, "\uE00C", "booster_x2.png", new String[]{"#542080", "#9747D9", "#DBB0FF"}),
    BOOSTER("booster", "BOOSTER", 125, "\uE00B", "booster.png", new String[]{"#542080", "#9747D9", "#DBB0FF"}),
    MEMBER("member", "MEMBER", 100, "\uE007", "member.png", new String[]{"#454B55", "#626975", "#AEB5C0"});

    private static final Map<String, Role> BY_ID = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(Role::id, Function.identity()));

    private final String id;
    private final String label;
    private final int weight;
    private final String glyph;
    private final String texture;
    private final String[] palette;

    Role(String id, String label, int weight, String glyph, String texture, String[] palette) {
        this.id = id;
        this.label = label;
        this.weight = weight;
        this.glyph = glyph;
        this.texture = texture;
        this.palette = palette;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public int weight() {
        return weight;
    }

    public String glyph() {
        return glyph;
    }

    public String texture() {
        return texture;
    }

    public String[] palette() {
        return palette.clone();
    }

    public boolean outranks(Role other) {
        return weight > other.weight;
    }

    public boolean isStaff() {
        return weight >= HELPER.weight;
    }

    public boolean canManageRoles() {
        return weight >= SSER.weight;
    }

    public static Optional<Role> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static Optional<Role> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT)
            .replace(' ', '_')
            .replace('-', '_');
        if ("coowner".equals(normalized)) {
            normalized = "co_owner";
        }
        if ("sradmin".equals(normalized) || "senior_admin".equals(normalized)) {
            normalized = "sr_admin";
        }
        if ("ssr".equals(normalized)) normalized = "sser";
        if ("boosterx2".equals(normalized) || "boostx2".equals(normalized)) normalized = "booster_x2";
        if ("boost".equals(normalized)) normalized = "booster";
        return byId(normalized);
    }
}

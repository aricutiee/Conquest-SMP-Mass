package dev.turtleroles.punishment;

public enum PunishmentType {
    WARNING(false),
    TEMP_MUTE(true),
    PERM_MUTE(false),
    TEMP_BAN(true),
    PERM_BAN(false),
    KICK(false);

    private final boolean temporary;

    PunishmentType(boolean temporary) {
        this.temporary = temporary;
    }

    public boolean temporary() {
        return temporary;
    }

    public boolean mute() {
        return this == TEMP_MUTE || this == PERM_MUTE;
    }

    public boolean ban() {
        return this == TEMP_BAN || this == PERM_BAN;
    }
}

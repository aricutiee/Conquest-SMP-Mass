package dev.turtleroles.anticheat;

/** Only Grim's built-in notification/storage actions are allowed, never console commands. */
public final class GrimPunishmentPolicy {
    public static final String ALERT_PREFIX = "[ConquestAC] ";
    private GrimPunishmentPolicy() { }
    public static boolean allows(String expandedAction) {
        if (expandedAction == null || expandedAction.indexOf('\n') >= 0 || expandedAction.indexOf('\r') >= 0) return false;
        // Grim expands [alert] into its message before posting CommandExecuteEvent.
        // Our messages.yml begins alerts-format with this literal, non-command prefix.
        return expandedAction.equals("[log]") || expandedAction.equals("[webhook]") || expandedAction.equals("[proxy]")
            || expandedAction.startsWith(ALERT_PREFIX);
    }
}

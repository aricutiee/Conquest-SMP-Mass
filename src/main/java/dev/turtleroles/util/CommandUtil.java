package dev.turtleroles.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;

public final class CommandUtil {
    private CommandUtil() {
    }

    public static void ok(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    public static void warn(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.YELLOW));
    }

    public static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    public static String reason(String[] args, int start, int maxLength) {
        if (args.length <= start) {
            throw new IllegalArgumentException("A reason is required.");
        }
        String value = String.join(" ", Arrays.copyOfRange(args, start, args.length)).trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("A reason is required.");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("Reason is too long.");
        }
        return value;
    }

    public static int page(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(args[index]));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}

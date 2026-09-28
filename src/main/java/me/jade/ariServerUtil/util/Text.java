package me.jade.ariServerUtil.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public final class Text {
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {
    }

    public static Component mm(String input) {
        return MINI.deserialize(input == null ? "" : input);
    }

    public static String plain(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static void send(CommandSender sender, String miniMessage) {
        sender.sendMessage(mm(miniMessage));
    }
}

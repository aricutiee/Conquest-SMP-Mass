package me.jade.ariServerUtil.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class Items {
    private Items() {
    }

    public static String serialize(ItemStack[] items) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                out.writeInt(items.length);
                for (ItemStack item : items) {
                    out.writeObject(item);
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Could not serialize item stacks", ex);
        }
    }

    public static ItemStack[] deserialize(String encoded) {
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
                int size = in.readInt();
                ItemStack[] items = new ItemStack[size];
                for (int i = 0; i < size; i++) {
                    items[i] = (ItemStack) in.readObject();
                }
                return items;
            }
        } catch (IOException | ClassNotFoundException ex) {
            throw new IllegalStateException("Could not deserialize item stacks", ex);
        }
    }

    public static ItemStack[] cloneContents(ItemStack[] input) {
        ItemStack[] copy = new ItemStack[input.length];
        for (int i = 0; i < input.length; i++) {
            copy[i] = input[i] == null ? null : input[i].clone();
        }
        return copy;
    }
}

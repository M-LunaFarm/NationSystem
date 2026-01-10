package kr.lunaf.nationSystem.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public final class ItemSerialization {
    private ItemSerialization() {
    }

    public static String serialize(ItemStack[] items) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public static ItemStack[] deserialize(String data) {
        if (data == null || data.isBlank()) {
            return new ItemStack[0];
        }
        byte[] raw = Base64.getDecoder().decode(data);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(raw);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            return items;
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }
}

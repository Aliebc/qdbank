package top.mc100.qdbank.common.util;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class ItemSerializer {
    public static final int BANK_SIZE = 54;

    private ItemSerializer() {}

    public static byte[] serialize(ItemStack[] items) {
        if (items.length != BANK_SIZE) throw new IllegalArgumentException("Bank inventory must contain 54 slots");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(BANK_SIZE);
                for (ItemStack item : items) {
                    byte[] data = item == null || item.isEmpty() ? new byte[0] : item.serializeAsBytes();
                    out.writeInt(data.length);
                    out.write(data);
                }
            }
            return buffer.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static ItemStack[] deserialize(byte[] content) {
        ItemStack[] items = new ItemStack[BANK_SIZE];
        if (content == null || content.length == 0) return items;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(content))) {
            int size = in.readInt();
            if (size != BANK_SIZE) throw new IllegalArgumentException("Invalid bank size: " + size);
            for (int slot = 0; slot < size; slot++) {
                int length = in.readInt();
                if (length < 0 || length > 2_097_152 || length > in.available()) {
                    throw new IllegalArgumentException("Invalid item payload");
                }
                if (length > 0) items[slot] = ItemStack.deserializeBytes(in.readNBytes(length));
            }
            if (in.available() != 0) throw new IllegalArgumentException("Trailing inventory data");
            return items;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot deserialize inventory", exception);
        }
    }
}

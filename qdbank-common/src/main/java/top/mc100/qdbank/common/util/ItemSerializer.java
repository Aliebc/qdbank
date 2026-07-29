package top.mc100.qdbank.common.util;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Complete per-slot item storage backed by NBT-API.
 * Each BinaryInventory entry is named Slot:0 ... Slot:53 and contains one full item SNBT compound.
 */
public final class ItemSerializer {
    public static final int BANK_SIZE=54;
    // Temporarily disabled: trust NBT-API's cross-version conversion even when its normalized
    // output differs from the source SNBT. Keep the strict branch for later re-enablement.
    private static final boolean STRICT_PLACEHOLDER_CHECK=false;

    public record DecodedInventory(ItemStack[] items,byte[][] portableItems,boolean[] placeholders){}
    private record DecodedItem(ItemStack item,boolean placeholder){}

    private ItemSerializer(){}

    public static byte[] serialize(ItemStack[] items){return serialize(items,null,null);}

    public static byte[] serialize(ItemStack[] items,byte[][] preserved,boolean[] placeholders){
        if(items.length!=BANK_SIZE)throw new IllegalArgumentException("Bank inventory must contain 54 slots");
        byte[][] slots=new byte[BANK_SIZE][];
        for(int slot=0;slot<BANK_SIZE;slot++){
            boolean preserve=placeholders!=null&&slot<placeholders.length&&placeholders[slot]
                    &&preserved!=null&&slot<preserved.length&&preserved[slot]!=null;
            slots[slot]=preserve?preserved[slot].clone():serializeItem(items[slot]);
        }
        return BinaryInventory.encode(slots);
    }

    public static byte[] serializeItem(ItemStack item){
        if(item==null||item.isEmpty())return new byte[0];
        ReadWriteNBT itemNbt=NBT.itemStackToNBT(item);
        return itemNbt.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static ItemStack deserializeItem(byte[] data){
        if(data==null||data.length==0)return null;
        return decodeItem(data).item();
    }

    public static ItemStack[] deserialize(byte[] content){return deserializeDetailed(content).items();}

    public static DecodedInventory deserializeDetailed(byte[] content){
        ItemStack[] items=new ItemStack[BANK_SIZE];
        byte[][] portable=BinaryInventory.decode(content);
        boolean[] placeholders=new boolean[BANK_SIZE];
        for(int slot=0;slot<BANK_SIZE;slot++){
            if(portable[slot].length==0)continue;
            DecodedItem decoded=decodeItem(portable[slot]);
            items[slot]=decoded.item();
            placeholders[slot]=decoded.placeholder();
        }
        return new DecodedInventory(items,cloneBytes(portable),placeholders);
    }

    private static DecodedItem decodeItem(byte[] data){
        try{
            ReadWriteNBT source=NBT.parseNBT(new String(data,StandardCharsets.UTF_8));
            ItemStack item=NBT.itemStackFromNBT(source);
            if(item==null||item.isEmpty())return placeholder("当前版本无法创建该物品");

            // Detect codecs that accept future data but silently discard an unknown component.
            ReadWriteNBT roundTrip=NBT.itemStackToNBT(item);
            if(STRICT_PLACEHOLDER_CHECK&&!source.equals(roundTrip)){
                return placeholder("当前版本无法完整保留该物品的全部 NBT 组件");
            }
            return new DecodedItem(item,false);
        }catch(Throwable exception){
            return placeholder("当前版本无法解析该物品的 NBT");
        }
    }

    private static DecodedItem placeholder(String reason){
        ItemStack item=new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("不兼容物品占位符",NamedTextColor.RED));
        meta.lore(List.of(
                Component.text(reason,NamedTextColor.GRAY),
                Component.text("原始 Slot NBT 已保留，不可操作",NamedTextColor.DARK_RED)
        ));
        item.setItemMeta(meta);
        return new DecodedItem(item,true);
    }

    private static byte[][] cloneBytes(byte[][] source){
        return Arrays.stream(source).map(bytes->bytes==null?new byte[0]:bytes.clone()).toArray(byte[][]::new);
    }
}

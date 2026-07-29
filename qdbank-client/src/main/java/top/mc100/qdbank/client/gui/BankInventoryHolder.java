package top.mc100.qdbank.client.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import top.mc100.qdbank.common.util.ItemSerializer;

public final class BankInventoryHolder implements InventoryHolder {
    private final UUID owner;
    private final String accountName;
    private final boolean readOnly;
    private final Inventory inventory;
    private long revision;
    private boolean applyingSync;
    private boolean saving;
    private ItemStack[] authoritative;
    private byte[][] portableItems;
    private boolean[] placeholders;

    public BankInventoryHolder(UUID owner,String username,String accountName,boolean readOnly,long revision,ItemSerializer.DecodedInventory decoded){
        this.owner=owner;this.accountName=accountName;this.readOnly=readOnly;this.revision=revision;
        inventory=Bukkit.createInventory(this,54,"QDBANK | "+username+" | "+accountName);
        ItemStack[] contents=decoded.items();
        inventory.setContents(contents);
        authoritative=cloneItems(contents);
        portableItems=cloneBytes(decoded.portableItems());placeholders=decoded.placeholders().clone();
    }
    public UUID owner(){return owner;}
    public String accountName(){return accountName;}
    public boolean readOnly(){return readOnly;}
    public long revision(){return revision;}
    public void revision(long revision){this.revision=revision;}
    public boolean applyingSync(){return applyingSync;}
    public boolean saving(){return saving;}
    public void saving(boolean saving){this.saving=saving;}
    public ItemStack[] authoritative(){return cloneItems(authoritative);}
    public byte[][] portableItems(){return cloneBytes(portableItems);}
    public boolean[] placeholders(){return placeholders.clone();}
    public boolean isPlaceholder(int slot){return slot>=0&&slot<54&&placeholders[slot];}
    public boolean hasPlaceholders(){for(boolean value:placeholders)if(value)return true;return false;}
    public void apply(ItemSerializer.DecodedInventory decoded,long revision){
        applyingSync=true;inventory.setContents(decoded.items());authoritative=cloneItems(decoded.items());
        portableItems=cloneBytes(decoded.portableItems());placeholders=decoded.placeholders().clone();
        this.revision=revision;this.saving=false;applyingSync=false;
    }
    private static ItemStack[] cloneItems(ItemStack[] items){
        ItemStack[] result=new ItemStack[items.length];
        for(int i=0;i<items.length;i++)result[i]=items[i]==null?null:items[i].clone();
        return result;
    }
    private static byte[][] cloneBytes(byte[][] values){
        byte[][] result=new byte[values.length][];for(int i=0;i<values.length;i++)result[i]=values[i]==null?new byte[0]:values[i].clone();return result;
    }
    @Override public Inventory getInventory(){return inventory;}
}

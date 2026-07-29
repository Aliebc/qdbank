package top.mc100.qdbank.client.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class BankInventoryHolder implements InventoryHolder {
    private final UUID owner;
    private final String accountName;
    private final boolean readOnly;
    private final Inventory inventory;
    private long revision;
    private boolean applyingSync;
    private boolean saving;
    private ItemStack[] authoritative;

    public BankInventoryHolder(UUID owner,String username,String accountName,boolean readOnly,long revision,ItemStack[] contents){
        this.owner=owner;this.accountName=accountName;this.readOnly=readOnly;this.revision=revision;
        inventory=Bukkit.createInventory(this,54,"QDBANK | "+username+" | "+accountName);
        inventory.setContents(contents);
        authoritative=cloneItems(contents);
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
    public void apply(ItemStack[] items,long revision){applyingSync=true;inventory.setContents(items);authoritative=cloneItems(items);this.revision=revision;this.saving=false;applyingSync=false;}
    private static ItemStack[] cloneItems(ItemStack[] items){
        ItemStack[] result=new ItemStack[items.length];
        for(int i=0;i<items.length;i++)result[i]=items[i]==null?null:items[i].clone();
        return result;
    }
    @Override public Inventory getInventory(){return inventory;}
}

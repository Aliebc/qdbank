package top.mc100.qdbank.client.gui;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import top.mc100.qdbank.client.QdBankPlugin;

public final class BankInventoryListener implements Listener {
    private final QdBankPlugin plugin;
    public BankInventoryListener(QdBankPlugin plugin){this.plugin=plugin;}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder(false) instanceof BankInventoryHolder holder))return;
        if(holder.isPlaceholder(event.getRawSlot())||(holder.hasPlaceholders()&&event.getAction()==org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR)){
            event.setCancelled(true);event.getWhoClicked().sendMessage("§c该槽位是不兼容物品占位符，不能操作");return;
        }
        if(holder.readOnly()||holder.saving()||!plugin.bankClient().isConnected()){event.setCancelled(true);return;}
        String direction=event.getAction().name().startsWith("PICKUP")||(event.isShiftClick()&&event.getRawSlot()<54)?"TAKE":"PUT";
        String detail=direction+" action="+event.getAction().name();
        plugin.getServer().getScheduler().runTask(plugin,()->plugin.pushUpdate(holder,detail));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void drag(InventoryDragEvent event){
        if(!(event.getView().getTopInventory().getHolder(false) instanceof BankInventoryHolder holder))return;
        boolean touchesBank=event.getRawSlots().stream().anyMatch(slot->slot<54);
        if(!touchesBank)return;
        if(event.getRawSlots().stream().anyMatch(holder::isPlaceholder)){event.setCancelled(true);event.getWhoClicked().sendMessage("§c不能操作不兼容物品占位符");return;}
        if(holder.readOnly()||holder.saving()||!plugin.bankClient().isConnected()){event.setCancelled(true);return;}
        plugin.getServer().getScheduler().runTask(plugin,()->plugin.pushUpdate(holder,"PUT action=DRAG"));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void close(InventoryCloseEvent event){
        if(!(event.getInventory().getHolder(false) instanceof BankInventoryHolder holder))return;
        if(!holder.readOnly()&&plugin.bankClient().isConnected())plugin.pushUpdate(holder,"inventory close");
        plugin.closeRemote(holder);
    }
    public static void closeAll(QdBankPlugin plugin,String message){
        for(var player:plugin.getServer().getOnlinePlayers()){
            if(player.getOpenInventory().getTopInventory().getHolder(false) instanceof BankInventoryHolder){
                player.sendMessage(message);player.closeInventory();
            }
        }
    }
}

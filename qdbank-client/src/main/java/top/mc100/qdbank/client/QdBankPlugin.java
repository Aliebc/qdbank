package top.mc100.qdbank.client;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import top.mc100.qdbank.common.dto.*;
import top.mc100.qdbank.client.command.QdBankCommands;
import top.mc100.qdbank.client.config.ClientConfig;
import top.mc100.qdbank.client.gui.BankInventoryHolder;
import top.mc100.qdbank.client.gui.BankInventoryListener;
import top.mc100.qdbank.client.network.BankClient;
import top.mc100.qdbank.common.packet.*;
import top.mc100.qdbank.common.util.ItemSerializer;
import top.mc100.qdbank.common.util.AuditItemCodec;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;

public final class QdBankPlugin extends JavaPlugin implements BankClient.Listener {
    private BankClient bankClient;
    private final ConcurrentHashMap<Long,BankInventoryHolder> updateRequests=new ConcurrentHashMap<>();
    private volatile String serverVersion="?";
    @Override public void onEnable(){
        saveDefaultConfig();
        bankClient=new BankClient(ClientConfig.from(getConfig()),this);
        getServer().getPluginManager().registerEvents(new BankInventoryListener(this),this);
        new QdBankCommands(this).register();
        bankClient.start();
    }
    @Override public void onDisable(){BankInventoryListener.closeAll(this,"§c银行插件正在关闭");if(bankClient!=null)bankClient.close();}
    public BankClient bankClient(){return bankClient;}

    public void create(CommandSender sender,String target,String account){accountRequest(sender,target,account,AccountRequestPacket.Operation.CREATE);}
    public void delete(CommandSender sender,String target,String account){accountRequest(sender,target,account,AccountRequestPacket.Operation.DELETE);}
    public void list(CommandSender sender,String target){accountRequest(sender,target,"_",AccountRequestPacket.Operation.LIST);}
    public void open(CommandSender sender,String target,String account){
        if(!(sender instanceof Player viewer)){sender.sendMessage("§c该命令需要玩家执行");return;}
        if(target!=null){
            directory(null,players->{
                var match=players.stream().filter(p->p.playerName().equalsIgnoreCase(target)).findFirst();
                main(()->{
                    if(match.isEmpty())sender.sendMessage("§c银行中找不到玩家 "+target);
                    else openResolved(sender,viewer,UUID.fromString(match.get().uuid()),match.get().playerName(),account,true);
                });
            });
            return;
        }
        UUID owner=resolve(sender,null);if(owner==null)return;
        openResolved(sender,viewer,owner,viewer.getName(),account,false);
    }
    private void openResolved(CommandSender sender,Player viewer,UUID owner,String playerName,String account,boolean admin){
        long id=bankClient.nextRequestId();
        bankClient.request(id,new AccountRequestPacket(id,AccountRequestPacket.Operation.OPEN,owner.toString(),playerName,account,admin,context(sender)),packet->
                main(()->{
                    if(packet instanceof AccountResponsePacket response&&response.success()){
                        try{
                            ItemStack[] contents=ItemSerializer.deserialize(response.content());
                            viewer.openInventory(new BankInventoryHolder(owner,ownerName(owner),account,response.readOnly(),response.revision(),contents).getInventory());
                            viewer.sendMessage("§a"+response.message());
                        }catch(Exception exception){viewer.sendMessage("§c账户数据损坏，已拒绝打开");}
                    }else sender.sendMessage("§c"+message(packet));
                }));
    }
    private void accountRequest(CommandSender sender,String target,String account,AccountRequestPacket.Operation operation){
        UUID owner=resolve(sender,target);if(owner==null)return;
        long id=bankClient.nextRequestId();
        bankClient.request(id,new AccountRequestPacket(id,operation,owner.toString(),ownerName(owner),account,target!=null,context(sender)),packet->main(()->{
            if(packet instanceof AccountResponsePacket response){
                if(operation==AccountRequestPacket.Operation.LIST)sender.sendMessage("§6账户: §f"+(response.accounts().isEmpty()?"（无）":String.join(", ",response.accounts())));
                else sender.sendMessage((response.success()?"§a":"§c")+response.message());
            }else sender.sendMessage("§c"+message(packet));
        }));
    }
    public void pushUpdate(BankInventoryHolder holder,String detail){
        if(holder.readOnly()||holder.applyingSync()||holder.saving()||!bankClient.isConnected())return;
        byte[] content=ItemSerializer.serialize(holder.getInventory().getContents());
        long id=bankClient.nextRequestId();
        holder.saving(true);
        updateRequests.put(id,holder);
        String encoded=AuditItemCodec.encode(diff(holder.authoritative(),ItemSerializer.deserialize(content)));
        if(!bankClient.send(new ContainerUpdatePacket(id,holder.owner().toString(),holder.accountName(),holder.revision(),content,
                encoded,context(viewer(holder))))){
            updateRequests.remove(id);holder.saving(false);closeMatching(holder,"§c保存失败：连接已断开");
        }
    }
    public void closeRemote(BankInventoryHolder holder){bankClient.send(new AccountClosePacket(holder.owner().toString(),holder.accountName(),context(viewer(holder))));}
    public void status(CommandSender sender){
        sender.sendMessage("§6QDBANK §7| §f客户端 1.0.2 §7| §f服务端 "+serverVersion);
        sender.sendMessage(bankClient.isConnected()?"§a连接正常 §7| §f延迟 "+(bankClient.latencyMillis()<0?"等待心跳":bankClient.latencyMillis()+" ms"):"§c未连接（正在自动重连）");
        sender.sendMessage("§8Copyright © 2026 QDBANK · mc100.top. All rights reserved.");
    }
    public void reloadBank(CommandSender sender){
        reloadConfig();bankClient.reconfigure(ClientConfig.from(getConfig()));sender.sendMessage("§a配置已重载，正在重连");
    }
    public void lookup(CommandSender sender,String playerName,String range){
        UUID uuid=resolve(sender,playerName);if(uuid==null)return;
        long since=System.currentTimeMillis()-parseRange(range).toMillis();long id=bankClient.nextRequestId();
        bankClient.request(id,new AuditLookupPacket(id,uuid.toString(),since),packet->main(()->{
            if(packet instanceof AuditResultPacket result){sender.sendMessage("§6最近审计记录：");result.entries().forEach(entry->sendAudit(sender,entry));}
            else sender.sendMessage("§c"+message(packet));
        }));
    }
    public void rollback(CommandSender sender,String playerName,String range){
        UUID uuid=resolve(sender,playerName);if(uuid==null)return;
        long since=System.currentTimeMillis()-parseRange(range).toMillis(),id=bankClient.nextRequestId();
        bankClient.request(id,new RollbackPacket(id,uuid.toString(),since,context(sender)),packet->main(()->{
            if(packet instanceof RollbackResultPacket result)sender.sendMessage("§a回滚完成，恢复账户数："+result.restoredAccounts());
            else sender.sendMessage("§c"+message(packet));
        }));
    }
    public void directory(String playerUuid,Consumer<List<PlayerAccounts>> callback){
        long id=bankClient.nextRequestId();
        bankClient.request(id,new DirectoryRequestPacket(id,playerUuid==null?"":playerUuid),packet->{
            if(packet instanceof DirectoryResponsePacket response)callback.accept(response.players());
            else callback.accept(List.of());
        });
    }
    private Duration parseRange(String value){
        try{long number=Long.parseLong(value.substring(0,value.length()-1));return switch(value.charAt(value.length()-1)){case'h'->Duration.ofHours(number);case'd'->Duration.ofDays(number);case'm'->Duration.ofMinutes(number);default->Duration.ofDays(7);};}
        catch(Exception ignored){return Duration.ofDays(7);}
    }
    @SuppressWarnings("deprecation")
    private UUID resolve(CommandSender sender,String target){
        if(target==null){if(sender instanceof Player player)return player.getUniqueId();sender.sendMessage("§c控制台必须指定玩家");return null;}
        OfflinePlayer player=Bukkit.getOfflinePlayer(target);
        if(!player.hasPlayedBefore()&&!player.isOnline()){sender.sendMessage("§c找不到玩家 "+target);return null;}
        return player.getUniqueId();
    }
    @Override public void connected(String version){serverVersion=version;getLogger().info("已连接 qdbank-server "+version);}
    @Override public void disconnected(){updateRequests.clear();main(()->BankInventoryListener.closeAll(this,"§c银行连接已断开，界面已强制关闭"));getLogger().warning("银行连接断开，正在重连");}
    @Override public void synchronizedContainer(ContainerSyncPacket packet){main(()->{
        BankInventoryHolder requester=updateRequests.remove(packet.requestId());
        if(!packet.accepted()){
            if(requester!=null){
                try{requester.apply(ItemSerializer.deserialize(packet.content()),packet.revision());}
                catch(Exception e){closeMatching(requester,"§c同步数据异常，银行已关闭");return;}
                for(Player player:getServer().getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder(false)==requester)player.sendMessage("§e"+packet.message());
            }
            return;
        }
        for(Player player:getServer().getOnlinePlayers()){
            if(player.getOpenInventory().getTopInventory().getHolder(false) instanceof BankInventoryHolder holder
                    &&holder.owner().toString().equals(packet.playerUuid())&&holder.accountName().equals(packet.accountName())){
                try{
                    holder.apply(ItemSerializer.deserialize(packet.content()),packet.revision());
                }catch(Exception e){closeMatching(holder,"§c同步数据异常，银行已关闭");}
            }
        }
    });}
    private void closeMatching(BankInventoryHolder holder,String message){
        for(Player player:getServer().getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder(false)==holder){player.sendMessage(message);player.closeInventory();}
    }
    private void main(Runnable task){getServer().getScheduler().runTask(this,task);}
    private OperationContext context(CommandSender sender){
        if(sender instanceof Player player){Location l=player.getLocation();return new OperationContext(l.getWorld().getName(),l.getX(),l.getY(),l.getZ(),getConfig().getString("client.id","unknown"));}
        return new OperationContext("CONSOLE",0,0,0,getConfig().getString("client.id","unknown"));
    }
    private Player viewer(BankInventoryHolder holder){
        return getServer().getOnlinePlayers().stream().filter(p->p.getOpenInventory().getTopInventory().getHolder(false)==holder).findFirst().orElse(null);
    }
    private static java.util.List<AuditItemChange> diff(ItemStack[] before,ItemStack[] after){
        java.util.List<AuditItemChange> changes=new java.util.ArrayList<>();
        for(int i=0;i<54;i++){
            ItemStack old=before[i],now=after[i];
            if(old==null&&now==null)continue;
            if(old!=null&&now!=null&&old.isSimilar(now)){
                int delta=now.getAmount()-old.getAmount();if(delta!=0)changes.add(change(delta,delta>0?now:old));continue;
            }
            if(old!=null)changes.add(change(-old.getAmount(),old));
            if(now!=null)changes.add(change(now.getAmount(),now));
        }
        return changes;
    }
    private static AuditItemChange change(int amount,ItemStack item){return new AuditItemChange(amount,item.getType().getKey().toString(),item.serializeAsBytes());}
    private static void sendAudit(CommandSender sender,AuditEntry entry){
        OperationContext c=entry.context();
        Component line=Component.text(entry.timestamp()+" "+entry.action()+" ["+entry.accountName()+"] @ "+c.serverId()+"/"+c.world()+" "+
                Math.round(c.x())+","+Math.round(c.y())+","+Math.round(c.z())+" ",NamedTextColor.GRAY);
        if(entry.changes().isEmpty()){sender.sendMessage(line);return;}
        for(AuditItemChange change:entry.changes()){
            try{
                ItemStack item=ItemStack.deserializeBytes(change.itemBytes());
                Component itemName=item.displayName().color(NamedTextColor.BLUE).hoverEvent(item.asHoverEvent());
                sender.sendMessage(line.append(Component.text((change.amount()>0?"+":"")+change.amount()+" ",change.amount()>0?NamedTextColor.GREEN:NamedTextColor.RED)).append(itemName));
            }catch(Exception ignored){sender.sendMessage(line.append(Component.text(change.amount()+" "+change.material(),NamedTextColor.BLUE)));}
        }
    }
    @SuppressWarnings("deprecation")
    private String ownerName(UUID owner){
        String name=Bukkit.getOfflinePlayer(owner).getName();
        return name==null?owner.toString():name;
    }
    private static String message(Packet packet){return packet instanceof ErrorPacket e?e.message():packet instanceof AccountResponsePacket r?r.message():
            packet instanceof RollbackResultPacket r?r.message():"未知响应";}
}

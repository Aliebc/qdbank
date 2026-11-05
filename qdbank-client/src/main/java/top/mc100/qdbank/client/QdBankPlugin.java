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
import net.kyori.adventure.text.event.ClickEvent;
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
    private final ConcurrentHashMap<String,LookupSpec> lastLookups=new ConcurrentHashMap<>();
    private volatile String serverVersion="?";
    @Override public void onEnable(){
        saveDefaultConfig();
        bankClient=new BankClient(ClientConfig.from(getConfig()),this);
        getServer().getPluginManager().registerEvents(new BankInventoryListener(this),this);
        getServer().getPluginManager().registerEvents(new DefaultAccountListener(this),this);
        new QdBankCommands(this).register();
        bankClient.start();
    }
    @Override public void onDisable(){BankInventoryListener.closeAll(this,"§c银行插件正在关闭");if(bankClient!=null)bankClient.close();}
    public BankClient bankClient(){return bankClient;}
    public void ensureDefaultAccounts(Player player){
        long id=bankClient.nextRequestId();
        bankClient.request(id,new AccountRequestPacket(id,AccountRequestPacket.Operation.LIST,player.getUniqueId().toString(),
                player.getName(),"_",false,context(player)),ignored->{});
    }

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
                            var decoded=ItemSerializer.deserializeDetailed(response.content());
                            viewer.openInventory(new BankInventoryHolder(owner,ownerName(owner),account,response.readOnly(),response.revision(),decoded).getInventory());
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
        byte[] content=ItemSerializer.serialize(holder.getInventory().getContents(),holder.portableItems(),holder.placeholders());
        long id=bankClient.nextRequestId();
        holder.saving(true);
        updateRequests.put(id,holder);
        var decodedAfter=ItemSerializer.deserializeDetailed(content);
        String encoded=AuditItemCodec.encode(diff(holder.authoritative(),decodedAfter.items(),holder.portableItems(),decodedAfter.portableItems()));
        if(!bankClient.send(new ContainerUpdatePacket(id,holder.owner().toString(),holder.accountName(),holder.revision(),content,
                encoded,context(viewer(holder))))){
            updateRequests.remove(id);holder.saving(false);closeMatching(holder,"§c保存失败：连接已断开");
        }
    }
    public void closeRemote(BankInventoryHolder holder){bankClient.send(new AccountClosePacket(holder.owner().toString(),holder.accountName(),context(viewer(holder))));}
    public void status(CommandSender sender){
        sender.sendMessage("§6QDBank §7| §f客户端 1.0.4 §7| §f服务端 "+serverVersion);
        sender.sendMessage(bankClient.isConnected()?"§a连接正常 §7| §f延迟 "+(bankClient.latencyMillis()<0?"等待心跳":bankClient.latencyMillis()+" ms"):"§c未连接（正在自动重连）");
        sender.sendMessage("§8Copyright © 2026 QDBank · mc100.top. All rights reserved.");
    }
    public void reloadBank(CommandSender sender){
        reloadConfig();bankClient.reconfigure(ClientConfig.from(getConfig()));sender.sendMessage("§a配置已重载，正在重连");
    }
    public void lookup(CommandSender sender,String arguments){
        LookupSpec spec=parseSpec(arguments,1);lastLookups.put(senderKey(sender),spec);executeLookup(sender,spec);
    }
    public void page(CommandSender sender,int page){
        LookupSpec previous=lastLookups.get(senderKey(sender));
        if(previous==null){sender.sendMessage("§c请先执行一次 lookup");return;}
        LookupSpec next=previous.withPage(page);lastLookups.put(senderKey(sender),next);executeLookup(sender,next);
    }
    private void executeLookup(CommandSender sender,LookupSpec spec){
        resolveBankUser(spec.user(),uuid->{
            if(uuid==null){main(()->sender.sendMessage("§c银行中找不到玩家 "+spec.user()));return;}
            long id=bankClient.nextRequestId(),since=System.currentTimeMillis()-parseRange(spec.time()).toMillis();
            bankClient.request(id,new AuditLookupPacket(id,uuid,since,spec.action(),spec.include(),spec.exclude(),spec.page(),spec.pageSize()),packet->main(()->{
                if(packet instanceof AuditResultPacket result){
                    sender.sendMessage("§6----- QDBank Lookup §7("+result.page()+"/"+result.totalPages()+") §6-----");
                    result.entries().forEach(entry->sendAudit(sender,entry));
                    Component nav=Component.text("« 上一页",NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/qdbank admin page "+Math.max(1,result.page()-1)))
                            .append(Component.text("  |  ",NamedTextColor.DARK_GRAY))
                            .append(Component.text("下一页 »",NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/qdbank admin page "+Math.min(result.totalPages(),result.page()+1))));
                    sender.sendMessage(nav);
                }else sender.sendMessage("§c"+message(packet));
            }));
        });
    }
    public void rollback(CommandSender sender,String arguments){
        LookupSpec spec=parseSpec(arguments,1);
        resolveBankUser(spec.user(),uuid->{
            if(uuid==null){main(()->sender.sendMessage("§c银行中找不到玩家 "+spec.user()));return;}
            long since=System.currentTimeMillis()-parseRange(spec.time()).toMillis(),id=bankClient.nextRequestId();
            bankClient.request(id,new RollbackPacket(id,uuid,since,spec.action(),spec.include(),spec.exclude(),context(sender)),packet->main(()->{
                if(packet instanceof RollbackResultPacket result)sender.sendMessage("§a回滚完成，恢复物品变更数："+result.restoredAccounts());
                else sender.sendMessage("§c"+message(packet));
            }));
        });
    }
    public void directory(String playerUuid,Consumer<List<PlayerAccounts>> callback){
        long id=bankClient.nextRequestId();
        bankClient.request(id,new DirectoryRequestPacket(id,playerUuid==null?"":playerUuid),packet->{
            if(packet instanceof DirectoryResponsePacket response)callback.accept(response.players());
            else callback.accept(List.of());
        });
    }
    private void resolveBankUser(String name,Consumer<String> callback){
        directory(null,players->callback.accept(players.stream().filter(p->p.playerName().equalsIgnoreCase(name)).map(PlayerAccounts::uuid).findFirst().orElse(null)));
    }
    private Duration parseRange(String value){
        try{
            java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)([wdhms])").matcher(value.toLowerCase());
            double seconds=0;while(matcher.find()){double n=Double.parseDouble(matcher.group(1));seconds+=n*switch(matcher.group(2)){case"w"->604800;case"d"->86400;case"h"->3600;case"m"->60;default->1;};}
            return seconds>0?Duration.ofMillis((long)(seconds*1000)):Duration.ofDays(7);
        }catch(Exception ignored){return Duration.ofDays(7);}
    }
    private LookupSpec parseSpec(String arguments,int defaultPage){
        java.util.Map<String,String> values=new java.util.HashMap<>();
        for(String token:arguments.trim().split("\\s+")){int colon=token.indexOf(':');if(colon>0)values.put(token.substring(0,colon).toLowerCase(),token.substring(colon+1));}
        return new LookupSpec(values.getOrDefault("user",values.getOrDefault("u","")),
                values.getOrDefault("action",values.getOrDefault("a","inventory")),
                values.getOrDefault("time",values.getOrDefault("t","30m")),
                values.getOrDefault("include",values.getOrDefault("i","")),
                values.getOrDefault("exclude",values.getOrDefault("e","")),defaultPage,10);
    }
    private static String senderKey(CommandSender sender){return sender instanceof Player p?p.getUniqueId().toString():"CONSOLE";}
    private record LookupSpec(String user,String action,String time,String include,String exclude,int page,int pageSize){
        LookupSpec withPage(int value){return new LookupSpec(user,action,time,include,exclude,value,pageSize);}
    }
    @SuppressWarnings("deprecation")
    private UUID resolve(CommandSender sender,String target){
        if(target==null){if(sender instanceof Player player)return player.getUniqueId();sender.sendMessage("§c控制台必须指定玩家");return null;}
        OfflinePlayer player=Bukkit.getOfflinePlayer(target);
        if(!player.hasPlayedBefore()&&!player.isOnline()){sender.sendMessage("§c找不到玩家 "+target);return null;}
        return player.getUniqueId();
    }
    @Override public void connected(String version){
        serverVersion=version;getLogger().info("已连接 qdbank-server "+version);
        main(()->getServer().getOnlinePlayers().forEach(this::ensureDefaultAccounts));
    }
    @Override public void disconnected(){updateRequests.clear();main(()->BankInventoryListener.closeAll(this,"§c银行连接已断开，界面已强制关闭"));getLogger().warning("银行连接断开，正在重连");}
    @Override public void synchronizedContainer(ContainerSyncPacket packet){main(()->{
        BankInventoryHolder requester=updateRequests.remove(packet.requestId());
        if(!packet.accepted()){
            if(requester!=null){
                try{requester.apply(ItemSerializer.deserializeDetailed(packet.content()),packet.revision());}
                catch(Exception e){closeMatching(requester,"§c同步数据异常，银行已关闭");return;}
                for(Player player:getServer().getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder(false)==requester)player.sendMessage("§e"+packet.message());
            }
            return;
        }
        for(Player player:getServer().getOnlinePlayers()){
            if(player.getOpenInventory().getTopInventory().getHolder(false) instanceof BankInventoryHolder holder
                    &&holder.owner().toString().equals(packet.playerUuid())&&holder.accountName().equals(packet.accountName())){
                try{
                    holder.apply(ItemSerializer.deserializeDetailed(packet.content()),packet.revision());
                }catch(Exception e){closeMatching(holder,"§c同步数据异常，银行已关闭");}
            }
        }
    });}
    private void closeMatching(BankInventoryHolder holder,String message){
        for(Player player:getServer().getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder(false)==holder){player.sendMessage(message);player.closeInventory();}
    }
    private void main(Runnable task){getServer().getScheduler().runTask(this,task);}
    private OperationContext context(CommandSender sender){
        if(sender instanceof Player player){Location l=player.getLocation();return new OperationContext(player.getUniqueId().toString(),player.getName(),l.getWorld().getName(),l.getX(),l.getY(),l.getZ(),getConfig().getString("client.id","unknown"));}
        return new OperationContext("CONSOLE","CONSOLE","CONSOLE",0,0,0,getConfig().getString("client.id","unknown"));
    }
    private Player viewer(BankInventoryHolder holder){
        return getServer().getOnlinePlayers().stream().filter(p->p.getOpenInventory().getTopInventory().getHolder(false)==holder).findFirst().orElse(null);
    }
    private static java.util.List<AuditItemChange> diff(ItemStack[] before,ItemStack[] after,byte[][] beforePortable,byte[][] afterPortable){
        java.util.List<AuditItemChange> changes=new java.util.ArrayList<>();
        for(int i=0;i<54;i++){
            ItemStack old=before[i],now=after[i];
            if(old==null&&now==null)continue;
            if(old!=null&&now!=null&&old.isSimilar(now)){
                int delta=now.getAmount()-old.getAmount();if(delta!=0)changes.add(change(i,delta,delta>0?now:old,beforePortable[i],afterPortable[i]));continue;
            }
            if(old!=null)changes.add(change(i,-old.getAmount(),old,beforePortable[i],afterPortable[i]));
            if(now!=null)changes.add(change(i,now.getAmount(),now,beforePortable[i],afterPortable[i]));
        }
        return changes;
    }
    private static AuditItemChange change(int slot,int amount,ItemStack item,byte[] before,byte[] after){
        return new AuditItemChange(slot,amount,item.getType().getKey().toString(),ItemSerializer.serializeItem(item),
                before==null?new byte[0]:before,after==null?new byte[0]:after);
    }
    private static void sendAudit(CommandSender sender,AuditEntry entry){
        OperationContext c=entry.context();
        Component line=Component.text("["+ago(entry.timestamp())+"] "+c.actorName()+" ",NamedTextColor.GRAY);
        if(entry.changes().isEmpty()){sender.sendMessage(line);sender.sendMessage("§8------");return;}
        for(AuditItemChange change:entry.changes()){
            try{
                ItemStack item=ItemSerializer.deserializeItem(change.itemBytes());
                Component itemName=item.displayName().color(NamedTextColor.BLUE).hoverEvent(item.asHoverEvent());
                sender.sendMessage(line.append(Component.text(change.amount()>0?"added x"+change.amount()+" ":"removed x"+(-change.amount())+" ",
                        change.amount()>0?NamedTextColor.GREEN:NamedTextColor.RED)).append(itemName)
                        .append(Component.text("\n("+Math.round(c.x())+"/"+Math.round(c.y())+"/"+Math.round(c.z())+"/"+c.world()+"/"+c.serverId()+")",NamedTextColor.GRAY)));
            }catch(Exception ignored){sender.sendMessage(line.append(Component.text(change.amount()+" "+change.material(),NamedTextColor.BLUE)));}
        }
        sender.sendMessage("§8------");
    }
    private static String ago(String timestamp){
        try{
            long seconds=Math.max(0,(System.currentTimeMillis()-Long.parseLong(timestamp))/1000);
            if(seconds<60)return seconds+"s ago";if(seconds<3600)return String.format(java.util.Locale.ROOT,"%.1fm ago",seconds/60d);
            if(seconds<86400)return String.format(java.util.Locale.ROOT,"%.1fh ago",seconds/3600d);
            return String.format(java.util.Locale.ROOT,"%.1fd ago",seconds/86400d);
        }catch(Exception ignored){return timestamp;}
    }
    @SuppressWarnings("deprecation")
    private String ownerName(UUID owner){
        String name=Bukkit.getOfflinePlayer(owner).getName();
        return name==null?owner.toString():name;
    }
    private static String message(Packet packet){return packet instanceof ErrorPacket e?e.message():packet instanceof AccountResponsePacket r?r.message():
            packet instanceof RollbackResultPacket r?r.message():"未知响应";}
}

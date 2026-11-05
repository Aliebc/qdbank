package top.mc100.qdbank.server.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import top.mc100.qdbank.common.ProtocolLimits;
import top.mc100.qdbank.common.dto.ActionType;
import top.mc100.qdbank.common.packet.*;
import top.mc100.qdbank.server.config.ServerConfig;
import top.mc100.qdbank.server.repository.AccountRepository;
import top.mc100.qdbank.server.repository.AuditLogRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

public final class ServerPacketHandler extends SimpleChannelInboundHandler<Packet> {
    private final ServerConfig config;
    private final SessionManager sessions;
    private final AccountRepository accounts;
    private final AuditLogRepository audits;
    private final Executor databaseExecutor;

    public ServerPacketHandler(ServerConfig config,SessionManager sessions,AccountRepository accounts,
                               AuditLogRepository audits,Executor databaseExecutor){
        this.config=config;this.sessions=sessions;this.accounts=accounts;this.audits=audits;this.databaseExecutor=databaseExecutor;
    }
    @Override protected void channelRead0(ChannelHandlerContext ctx,Packet packet){
        if(packet instanceof HandshakePacket p){handshake(ctx,p);return;}
        if(!sessions.authenticated(ctx.channel())){ctx.writeAndFlush(new ErrorPacket(0,"Handshake required")).addListener(f->ctx.close());return;}
        if(packet instanceof PingPacket p){ctx.writeAndFlush(new PongPacket(p.timestamp()));return;}
        databaseExecutor.execute(()->process(ctx,packet));
    }
    private void handshake(ChannelHandlerContext ctx,HandshakePacket p){
        boolean accepted=config.secretKey().equals(p.secretKey())&&config.clientIds().contains(p.clientId())&&"1.0.4".equals(p.version());
        if(accepted)sessions.authenticate(ctx.channel(),p.clientId());
        ctx.writeAndFlush(new HandshakeResultPacket(accepted,"1.0.4",accepted?"Authenticated":"Invalid client, key, or version"));
        if(!accepted)ctx.executor().schedule((Runnable)ctx::close,1,java.util.concurrent.TimeUnit.SECONDS);
    }
    private void process(ChannelHandlerContext ctx,Packet packet){
        try{
            if(packet instanceof AccountRequestPacket p)account(ctx,p);
            else if(packet instanceof ContainerUpdatePacket p)update(ctx,p);
            else if(packet instanceof AccountClosePacket p)close(ctx,p);
            else if(packet instanceof AuditLookupPacket p){
                var page=audits.lookup(validUuid(p.playerUuid()),p.sinceEpochMillis(),p.action(),p.include(),p.exclude(),p.page(),p.pageSize());
                ctx.writeAndFlush(new AuditResultPacket(p.requestId(),page.page(),page.totalPages(),page.entries()));
            }
            else if(packet instanceof RollbackPacket p)rollback(ctx,p);
            else if(packet instanceof DirectoryRequestPacket p)ctx.writeAndFlush(
                    new DirectoryResponsePacket(p.requestId(),accounts.directory(p.playerUuid().isBlank()?null:validUuid(p.playerUuid()))));
        }catch(Exception exception){
            long id=packet instanceof AccountRequestPacket p?p.requestId():packet instanceof ContainerUpdatePacket p?p.requestId():
                    packet instanceof AuditLookupPacket p?p.requestId():packet instanceof RollbackPacket p?p.requestId():
                    packet instanceof DirectoryRequestPacket p?p.requestId():0;
            ctx.writeAndFlush(new ErrorPacket(id,"Request failed: "+exception.getMessage()));
        }
    }
    private void account(ChannelHandlerContext ctx,AccountRequestPacket p)throws Exception{
        String uuid=validUuid(p.playerUuid());String playerName=validPlayerName(p.playerName());
        String name=validName(p.accountName());String client=sessions.clientId(ctx.channel());
        ensureDefaultAccounts(uuid,playerName,client,p.context());
        switch(p.operation()){
            case CREATE->{boolean ok=accounts.create(uuid,playerName,name);if(ok)audits.append(uuid,name,ActionType.CREATE,"",client,p.context());respond(ctx,p,ok,ok?"Account created":"Account already exists",List.of(),new byte[0],0,false);}
            case DELETE->{
                if(name.equals("checking")||name.equals("saving")){
                    respond(ctx,p,false,"Default accounts cannot be deleted",List.of(),new byte[0],0,false);
                    return;
                }
                SessionManager.AccountKey key=new SessionManager.AccountKey(uuid,name);
                boolean ok=!sessions.isOpen(key)&&accounts.delete(uuid,name);
                if(ok)audits.append(uuid,name,ActionType.DELETE,"",client,p.context());
                respond(ctx,p,ok,ok?"Account deleted":"Account is open or does not exist",List.of(),new byte[0],0,false);
            }
            case LIST->respond(ctx,p,true,"OK",accounts.list(uuid),new byte[0],0,false);
            case OPEN->{
                var account=accounts.find(uuid,name);
                if(account.isEmpty()){respond(ctx,p,false,"Account does not exist",List.of(),new byte[0],0,false);return;}
                SessionManager.AccountKey key=new SessionManager.AccountKey(uuid,name);
                sessions.observe(key,ctx.channel());
                audits.append(uuid,name,ActionType.OPEN,"",client,p.context());
                respond(ctx,p,true,"Opened",List.of(),account.get().content(),account.get().revision(),false);
            }
        }
    }
    private void update(ChannelHandlerContext ctx,ContainerUpdatePacket p)throws Exception{
        String uuid=validUuid(p.playerUuid());String name=validName(p.accountName());
        SessionManager.AccountKey key=new SessionManager.AccountKey(uuid,name);
        if(!sessions.canEdit(key,ctx.channel())){
            var current=accounts.find(uuid,name).orElseThrow();
            ctx.writeAndFlush(new ContainerSyncPacket(p.requestId(),uuid,name,current.revision(),current.content(),false,"Account is not open"));
            return;
        }
        if(p.content().length>ProtocolLimits.MAX_SIZE_BYTES)throw new IllegalArgumentException("Inventory payload too large");
        boolean updated=accounts.update(uuid,name,p.expectedRevision(),p.content());
        if(!updated){
            var current=accounts.find(uuid,name).orElseThrow();
            ctx.writeAndFlush(new ContainerSyncPacket(p.requestId(),uuid,name,current.revision(),current.content(),false,
                    "更新冲突：另一操作已先提交，本次操作已拒绝"));
            return;
        }
        long revision=p.expectedRevision()+1;
        boolean removed=top.mc100.qdbank.common.util.AuditItemCodec.decode(p.itemDetail()).stream().anyMatch(change->change.amount()<0);
        ActionType action=removed?ActionType.TAKE:ActionType.PUT;
        if(!p.itemDetail().isBlank())audits.append(uuid,name,action,p.itemDetail(),sessions.clientId(ctx.channel()),p.context());
        sessions.broadcast(key,new ContainerSyncPacket(p.requestId(),uuid,name,revision,p.content(),true,"Saved"));
    }
    private void close(ChannelHandlerContext ctx,AccountClosePacket p)throws Exception{
        String uuid=validUuid(p.playerUuid());String name=validName(p.accountName());
        sessions.close(new SessionManager.AccountKey(uuid,name),ctx.channel());
        audits.append(uuid,name,ActionType.CLOSE,"",sessions.clientId(ctx.channel()),p.context());
    }
    private void respond(ChannelHandlerContext ctx,AccountRequestPacket p,boolean success,String message,List<String> list,byte[] content,long revision,boolean readOnly){
        ctx.writeAndFlush(new AccountResponsePacket(p.requestId(),success,message,p.playerUuid(),p.accountName(),list,content,revision,readOnly));
    }
    private void ensureDefaultAccounts(String uuid,String playerName,String clientId,top.mc100.qdbank.common.dto.OperationContext context)throws Exception{
        if(accounts.create(uuid,playerName,"checking"))audits.append(uuid,"checking",ActionType.CREATE,"",clientId,context);
        if(accounts.create(uuid,playerName,"saving"))audits.append(uuid,"saving",ActionType.CREATE,"",clientId,context);
    }
    private void rollback(ChannelHandlerContext ctx,RollbackPacket p)throws Exception{
        String uuid=validUuid(p.playerUuid());
        var changes=audits.rollbackChanges(uuid,p.sinceEpochMillis());
        java.util.Map<String,byte[][]> inventories=new java.util.LinkedHashMap<>();
        int restoredItems=0;
        java.util.Set<String> includes=csv(p.include()),excludes=csv(p.exclude());
        for(var record:changes){
            byte[][] slots=inventories.get(record.account());
            if(slots==null){
                slots=top.mc100.qdbank.common.util.BinaryInventory.decode(accounts.find(uuid,record.account()).orElseThrow().content());
                inventories.put(record.account(),slots);
            }
            for(var change:record.changes()){
                String material=change.material().replace("minecraft:","").toLowerCase();
                if((!includes.isEmpty()&&!includes.contains(material))||excludes.contains(material))continue;
                if(p.action().equals("+inventory")&&change.amount()<=0)continue;
                if(p.action().equals("-inventory")&&change.amount()>=0)continue;
                if(!p.action().isBlank()&&!p.action().equals("inventory")&&!p.action().equals("+inventory")&&!p.action().equals("-inventory"))continue;
                if(change.slot()<0||change.slot()>=54)continue;
                if(java.util.Arrays.equals(slots[change.slot()],change.afterItem())){
                    slots[change.slot()]=change.beforeItem();restoredItems++;
                }
            }
        }
        for(var entry:inventories.entrySet()){
            byte[] content=top.mc100.qdbank.common.util.BinaryInventory.encode(entry.getValue());
            long revision=accounts.forceUpdate(uuid,entry.getKey(),content);
            SessionManager.AccountKey key=new SessionManager.AccountKey(uuid,entry.getKey());
            sessions.broadcast(key,new ContainerSyncPacket(p.requestId(),uuid,entry.getKey(),revision,content,true,"Rollback"));
            audits.append(uuid,entry.getKey(),ActionType.ROLLBACK,"",sessions.clientId(ctx.channel()),p.context());
        }
        ctx.writeAndFlush(new RollbackResultPacket(p.requestId(),true,restoredItems,"Rollback complete"));
    }
    private static java.util.Set<String> csv(String value){
        if(value==null||value.isBlank())return java.util.Set.of();
        return java.util.Arrays.stream(value.toLowerCase().split(",")).map(v->v.replace("minecraft:","")).collect(java.util.stream.Collectors.toSet());
    }
    private static String validUuid(String value){return UUID.fromString(value).toString();}
    private static String validName(String value){
        if(value==null||!value.matches("[\\p{L}\\p{N}_-]{1,32}"))throw new IllegalArgumentException("Invalid account name");
        return value;
    }
    private static String validPlayerName(String value){
        if(value==null||!value.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Invalid player name");
        return value;
    }
    @Override public void channelInactive(ChannelHandlerContext ctx){sessions.remove(ctx.channel());}
    @Override public void exceptionCaught(ChannelHandlerContext ctx,Throwable cause){cause.printStackTrace();ctx.close();}
}

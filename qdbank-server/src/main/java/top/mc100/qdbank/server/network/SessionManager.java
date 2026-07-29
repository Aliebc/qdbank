package top.mc100.qdbank.server.network;

import io.netty.channel.Channel;
import top.mc100.qdbank.common.packet.Packet;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SessionManager {
    public record AccountKey(String uuid,String name){}
    private final ConcurrentHashMap<Channel,String> clients=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AccountKey,ConcurrentHashMap<Channel,AtomicInteger>> observers=new ConcurrentHashMap<>();

    public void authenticate(Channel channel,String clientId){clients.put(channel,clientId);}
    public boolean authenticated(Channel channel){return clients.containsKey(channel);}
    public String clientId(Channel channel){return clients.getOrDefault(channel,"unknown");}
    public void observe(AccountKey key,Channel channel){
        observers.computeIfAbsent(key,k->new ConcurrentHashMap<>())
                .computeIfAbsent(channel,ch->new AtomicInteger()).incrementAndGet();
    }
    public boolean canEdit(AccountKey key,Channel channel){
        var channels=observers.get(key);
        return channels!=null&&channels.containsKey(channel);
    }
    public boolean isOpen(AccountKey key){return observers.containsKey(key);}
    public void close(AccountKey key,Channel channel){
        observers.computeIfPresent(key,(k,channels)->{
            channels.computeIfPresent(channel,(ch,count)->count.decrementAndGet()<=0?null:count);
            return channels.isEmpty()?null:channels;
        });
    }
    public void broadcast(AccountKey key,Packet packet){
        var channels=observers.get(key);
        if(channels!=null)channels.keySet().forEach(ch->{if(ch.isActive())ch.writeAndFlush(packet);});
    }
    public void remove(Channel channel){
        clients.remove(channel);
        observers.forEach((key,channels)->channels.remove(channel));
        observers.entrySet().removeIf(e->e.getValue().isEmpty());
    }
}

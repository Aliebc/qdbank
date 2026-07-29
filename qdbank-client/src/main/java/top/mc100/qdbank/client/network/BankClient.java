package top.mc100.qdbank.client.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import top.mc100.qdbank.client.config.ClientConfig;
import top.mc100.qdbank.common.network.NetworkPipeline;
import top.mc100.qdbank.common.packet.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class BankClient implements AutoCloseable {
    public interface Listener{
        void connected(String version);
        void disconnected();
        void synchronizedContainer(ContainerSyncPacket packet);
    }
    private final EventLoopGroup group=new NioEventLoopGroup(1);
    private final AtomicLong requestIds=new AtomicLong();
    private final ConcurrentHashMap<Long,Consumer<Packet>> pending=new ConcurrentHashMap<>();
    private final AtomicInteger attempts=new AtomicInteger();
    private final Listener listener;
    private volatile ClientConfig config;
    private volatile Channel channel;
    private volatile boolean authenticated;
    private volatile boolean stopping;
    private volatile long latencyMillis=-1;

    public BankClient(ClientConfig config,Listener listener){this.config=config;this.listener=listener;}
    public void start(){stopping=false;connect();}
    public void reconfigure(ClientConfig config){this.config=config;Channel old=channel;if(old!=null)old.close();else connect();}
    public boolean isConnected(){return authenticated&&channel!=null&&channel.isActive();}
    public long latencyMillis(){return latencyMillis;}
    public long nextRequestId(){return requestIds.incrementAndGet();}
    public void request(long id,Packet packet,Consumer<Packet> callback){
        if(!isConnected()){callback.accept(new ErrorPacket(id,"银行服务器未连接"));return;}
        pending.put(id,callback);channel.writeAndFlush(packet);
        channel.eventLoop().schedule(()->{Consumer<Packet> expired=pending.remove(id);if(expired!=null)expired.accept(new ErrorPacket(id,"请求超时"));},10,TimeUnit.SECONDS);
    }
    public boolean send(Packet packet){if(!isConnected())return false;channel.writeAndFlush(packet);return true;}
    private void connect(){
        if(stopping)return;
        new Bootstrap().group(group).channel(NioSocketChannel.class).option(ChannelOption.CONNECT_TIMEOUT_MILLIS,5000)
                .option(ChannelOption.TCP_NODELAY,true).handler(new ChannelInitializer<SocketChannel>(){
                    @Override protected void initChannel(SocketChannel ch){NetworkPipeline.install(ch.pipeline());ch.pipeline().addLast(new Handler());}
                }).connect(config.host(),config.port()).addListener((ChannelFutureListener)future->{
                    if(future.isSuccess()){channel=future.channel();future.channel().writeAndFlush(new HandshakePacket(config.id(),config.secretKey(),"1.0.2"));}
                    else scheduleReconnect();
                });
    }
    private void scheduleReconnect(){
        if(stopping)return;
        int seconds=Math.min(60,1<<Math.min(attempts.getAndIncrement(),6));
        group.schedule(this::connect,seconds,TimeUnit.SECONDS);
    }
    private final class Handler extends SimpleChannelInboundHandler<Packet>{
        @Override protected void channelRead0(ChannelHandlerContext ctx,Packet packet){
            if(packet instanceof HandshakeResultPacket p){
                authenticated=p.accepted();
                if(p.accepted()){attempts.set(0);listener.connected(p.serverVersion());ctx.executor().scheduleAtFixedRate(()->{if(ctx.channel().isActive())ctx.writeAndFlush(new PingPacket(System.currentTimeMillis()));},10,10,TimeUnit.SECONDS);}
                else ctx.close();
                return;
            }
            if(packet instanceof ContainerSyncPacket p){listener.synchronizedContainer(p);return;}
            if(packet instanceof PongPacket p){latencyMillis=Math.max(0,System.currentTimeMillis()-p.timestamp());return;}
            long id=packet instanceof AccountResponsePacket p?p.requestId():packet instanceof AuditResultPacket p?p.requestId():
                    packet instanceof RollbackResultPacket p?p.requestId():packet instanceof DirectoryResponsePacket p?p.requestId():
                    packet instanceof ErrorPacket p?p.requestId():-1;
            Consumer<Packet> callback=pending.remove(id);if(callback!=null)callback.accept(packet);
        }
        @Override public void channelInactive(ChannelHandlerContext ctx){
            authenticated=false;latencyMillis=-1;channel=null;pending.forEach((id,callback)->callback.accept(new ErrorPacket(id,"连接已断开")));pending.clear();
            listener.disconnected();scheduleReconnect();
        }
        @Override public void exceptionCaught(ChannelHandlerContext ctx,Throwable cause){ctx.close();}
    }
    @Override public void close(){stopping=true;authenticated=false;if(channel!=null)channel.close();group.shutdownGracefully();}
}

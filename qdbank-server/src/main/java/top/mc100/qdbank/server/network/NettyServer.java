package top.mc100.qdbank.server.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import top.mc100.qdbank.common.network.NetworkPipeline;
import top.mc100.qdbank.server.config.ServerConfig;
import top.mc100.qdbank.server.repository.AccountRepository;
import top.mc100.qdbank.server.repository.AuditLogRepository;

import java.util.concurrent.Executor;

public final class NettyServer implements AutoCloseable{
    private final EventLoopGroup boss=new NioEventLoopGroup(1);
    private final EventLoopGroup workers=new NioEventLoopGroup();
    private Channel channel;
    public void start(ServerConfig config,SessionManager sessions,AccountRepository accounts,AuditLogRepository audits,Executor databaseExecutor)throws InterruptedException{
        channel=new ServerBootstrap().group(boss,workers).channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY,true).childOption(ChannelOption.SO_KEEPALIVE,true)
                .childHandler(new ChannelInitializer<SocketChannel>(){
                    @Override protected void initChannel(SocketChannel ch){
                        NetworkPipeline.install(ch.pipeline());
                        ch.pipeline().addLast(new ServerPacketHandler(config,sessions,accounts,audits,databaseExecutor));
                    }}).bind(config.host(),config.port()).sync().channel();
    }
    public Channel channel(){return channel;}
    @Override public void close(){if(channel!=null)channel.close().syncUninterruptibly();boss.shutdownGracefully();workers.shutdownGracefully();}
}

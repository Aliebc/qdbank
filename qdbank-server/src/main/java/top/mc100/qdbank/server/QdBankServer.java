package top.mc100.qdbank.server;

import top.mc100.qdbank.server.config.ServerConfig;
import top.mc100.qdbank.server.database.Database;
import top.mc100.qdbank.server.network.NettyServer;
import top.mc100.qdbank.server.network.SessionManager;
import top.mc100.qdbank.server.repository.AccountRepository;
import top.mc100.qdbank.server.repository.AuditLogRepository;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class QdBankServer {
    private QdBankServer(){}
    public static void main(String[] args)throws Exception{
        Path configPath=Path.of(args.length==0?"config.yml":args[0]).toAbsolutePath();
        ServerConfig config=ServerConfig.load(configPath);
        Database database=new Database(config.databaseFile(),config.poolSize());
        // A single ordered authority queue prevents UPDATE and CLOSE packets from overtaking each other.
        ExecutorService databaseExecutor=Executors.newSingleThreadExecutor(Thread.ofPlatform().name("qdbank-db-",0).factory());
        NettyServer server=new NettyServer();
        server.start(config,new SessionManager(),new AccountRepository(database),new AuditLogRepository(database),databaseExecutor);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{server.close();databaseExecutor.shutdown();database.close();},"qdbank-shutdown"));
        System.out.printf("qdbank-server 1.0.2 listening on %s:%d%n",config.host(),config.port());
        server.channel().closeFuture().sync();
    }
}

package top.mc100.qdbank.client.config;

import org.bukkit.configuration.file.FileConfiguration;

public record ClientConfig(String id,String secretKey,String host,int port){
    public static ClientConfig from(FileConfiguration config){
        return new ClientConfig(required(config,"client.id"),required(config,"client.secret_key"),
                required(config,"client.server_host"),config.getInt("client.server_port",25565));
    }
    private static String required(FileConfiguration config,String path){
        String value=config.getString(path);
        if(value==null||value.isBlank())throw new IllegalArgumentException("Missing config value: "+path);
        return value;
    }
}

package top.mc100.qdbank.server.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ServerConfig(String host, int port, String secretKey, Set<String> clientIds,
                           Path databaseFile, int poolSize) {
    @SuppressWarnings("unchecked")
    public static ServerConfig load(Path file) throws IOException {
        if (Files.notExists(file)) {
            try (InputStream source = ServerConfig.class.getResourceAsStream("/config.yml")) {
                if (source == null) throw new IOException("Bundled config.yml is missing");
                Files.copy(source, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Map<String,Object> root;
        try (InputStream input = Files.newInputStream(file)) {
            root = new Yaml().load(input);
        }
        Map<String,Object> server = (Map<String,Object>) root.get("server");
        Map<String,Object> database = (Map<String,Object>) root.get("database");
        Set<String> ids = new HashSet<>();
        for (Map<String,Object> client : (List<Map<String,Object>>) root.getOrDefault("clients", List.of())) {
            ids.add(String.valueOf(client.get("id")));
        }
        String secret = String.valueOf(server.get("secret_key"));
        if ("CHANGE_ME_SECRET_KEY".equals(secret)) {
            System.err.println("WARNING: default secret_key is unsafe; change it before production use.");
        }
        return new ServerConfig(String.valueOf(server.get("host")), ((Number)server.get("port")).intValue(),
                secret, Set.copyOf(ids), file.toAbsolutePath().getParent().resolve(String.valueOf(database.get("file"))),
                ((Number)database.get("pool_size")).intValue());
    }
}

package top.mc100.qdbank.common.dto;

public record OperationContext(String world, double x, double y, double z, String serverId) {
    public OperationContext {
        world = world == null ? "CONSOLE" : world;
        serverId = serverId == null ? "unknown" : serverId;
    }
}

package top.mc100.qdbank.common.dto;

public record OperationContext(String actorUuid, String actorName, String world,
                               double x, double y, double z, String serverId) {
    public OperationContext {
        actorUuid = actorUuid == null ? "CONSOLE" : actorUuid;
        actorName = actorName == null ? "CONSOLE" : actorName;
        world = world == null ? "CONSOLE" : world;
        serverId = serverId == null ? "unknown" : serverId;
    }
}

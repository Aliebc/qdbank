package top.mc100.qdbank.common.packet;

import top.mc100.qdbank.common.dto.OperationContext;

public record RollbackPacket(long requestId, String playerUuid, long sinceEpochMillis,
                             String action, String include, String exclude,
                             OperationContext context) implements Packet {}

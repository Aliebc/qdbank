package top.mc100.qdbank.common.packet;

import top.mc100.qdbank.common.dto.OperationContext;

public record RollbackPacket(long requestId, String playerUuid, long sinceEpochMillis,
                             OperationContext context) implements Packet {}

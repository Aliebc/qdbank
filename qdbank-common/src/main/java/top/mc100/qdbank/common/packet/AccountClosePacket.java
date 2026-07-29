package top.mc100.qdbank.common.packet;

import top.mc100.qdbank.common.dto.OperationContext;

public record AccountClosePacket(String playerUuid, String accountName, OperationContext context) implements Packet {}

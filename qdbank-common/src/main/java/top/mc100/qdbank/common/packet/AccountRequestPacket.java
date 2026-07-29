package top.mc100.qdbank.common.packet;

import top.mc100.qdbank.common.dto.OperationContext;

public record AccountRequestPacket(long requestId, Operation operation, String playerUuid,
                                   String playerName, String accountName, boolean admin,
                                   OperationContext context) implements Packet {
    public enum Operation { CREATE, DELETE, LIST, OPEN }
}

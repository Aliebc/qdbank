package top.mc100.qdbank.common.packet;

import top.mc100.qdbank.common.dto.OperationContext;

public record ContainerUpdatePacket(long requestId, String playerUuid, String accountName,
                                    long expectedRevision, byte[] content, String itemDetail,
                                    OperationContext context) implements Packet {
    public ContainerUpdatePacket {
        content = content == null ? new byte[0] : content.clone();
    }
}

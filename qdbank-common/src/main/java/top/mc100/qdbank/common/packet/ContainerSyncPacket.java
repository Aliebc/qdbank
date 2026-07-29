package top.mc100.qdbank.common.packet;

public record ContainerSyncPacket(long requestId, String playerUuid, String accountName, long revision,
                                  byte[] content, boolean accepted, String message) implements Packet {
    public ContainerSyncPacket {
        content = content == null ? new byte[0] : content.clone();
    }
}

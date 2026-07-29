package top.mc100.qdbank.common.packet;

public record DirectoryRequestPacket(long requestId, String playerUuid) implements Packet {}

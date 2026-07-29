package top.mc100.qdbank.common.packet;

public record HandshakePacket(String clientId, String secretKey, String version) implements Packet {}

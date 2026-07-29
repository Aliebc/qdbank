package top.mc100.qdbank.common.packet;

public record HandshakeResultPacket(boolean accepted, String serverVersion, String message) implements Packet {}

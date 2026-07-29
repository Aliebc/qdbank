package top.mc100.qdbank.common.packet;

public record ErrorPacket(long requestId, String message) implements Packet {}

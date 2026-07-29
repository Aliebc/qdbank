package top.mc100.qdbank.common.packet;

public record AuditLookupPacket(long requestId, String playerUuid, long sinceEpochMillis) implements Packet {}

package top.mc100.qdbank.common.packet;

public record AuditLookupPacket(long requestId, String playerUuid, long sinceEpochMillis,
                                String action, String include, String exclude,
                                int page, int pageSize) implements Packet {}

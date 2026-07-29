package top.mc100.qdbank.common.packet;

import top.mc100.qdbank.common.dto.AuditEntry;
import java.util.List;

public record AuditResultPacket(long requestId, List<AuditEntry> entries) implements Packet {
    public AuditResultPacket { entries = entries == null ? List.of() : List.copyOf(entries); }
}

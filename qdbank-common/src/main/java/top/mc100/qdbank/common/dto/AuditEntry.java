package top.mc100.qdbank.common.dto;

import java.util.List;

public record AuditEntry(String timestamp, String accountName, ActionType action,
                         List<AuditItemChange> changes, OperationContext context) {
    public AuditEntry { changes = changes == null ? List.of() : List.copyOf(changes); }
}

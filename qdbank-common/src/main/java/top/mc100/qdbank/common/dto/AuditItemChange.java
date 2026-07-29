package top.mc100.qdbank.common.dto;

public record AuditItemChange(int amount, String material, byte[] itemBytes) {
    public AuditItemChange { itemBytes = itemBytes == null ? new byte[0] : itemBytes.clone(); }
}

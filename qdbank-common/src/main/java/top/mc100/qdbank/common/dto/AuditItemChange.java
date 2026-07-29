package top.mc100.qdbank.common.dto;

public record AuditItemChange(int slot, int amount, String material, byte[] itemBytes,
                              byte[] beforeItem, byte[] afterItem) {
    public AuditItemChange {
        itemBytes = itemBytes == null ? new byte[0] : itemBytes.clone();
        beforeItem = beforeItem == null ? new byte[0] : beforeItem.clone();
        afterItem = afterItem == null ? new byte[0] : afterItem.clone();
    }
}

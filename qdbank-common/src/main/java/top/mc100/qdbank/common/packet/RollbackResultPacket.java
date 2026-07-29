package top.mc100.qdbank.common.packet;

public record RollbackResultPacket(long requestId, boolean success, int restoredAccounts,
                                   String message) implements Packet {}

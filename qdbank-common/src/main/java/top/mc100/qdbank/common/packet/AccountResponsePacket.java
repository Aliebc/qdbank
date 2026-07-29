package top.mc100.qdbank.common.packet;

import java.util.List;

public record AccountResponsePacket(long requestId, boolean success, String message,
                                    String playerUuid, String accountName, List<String> accounts,
                                    byte[] content, long revision, boolean readOnly) implements Packet {
    public AccountResponsePacket {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        content = content == null ? new byte[0] : content.clone();
    }
}

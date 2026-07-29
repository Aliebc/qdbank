package top.mc100.qdbank.common.packet;

import top.mc100.qdbank.common.dto.PlayerAccounts;
import java.util.List;

public record DirectoryResponsePacket(long requestId, List<PlayerAccounts> players) implements Packet {
    public DirectoryResponsePacket { players = players == null ? List.of() : List.copyOf(players); }
}

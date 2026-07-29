package top.mc100.qdbank.common.packet;

public sealed interface Packet permits HandshakePacket, HandshakeResultPacket, PingPacket, PongPacket,
        AccountRequestPacket, AccountResponsePacket, ContainerUpdatePacket, ContainerSyncPacket,
        AccountClosePacket, AuditLookupPacket, AuditResultPacket, RollbackPacket,
        RollbackResultPacket, DirectoryRequestPacket, DirectoryResponsePacket, ErrorPacket {
}

package top.mc100.qdbank.common.network;

import io.netty.buffer.ByteBuf;
import top.mc100.qdbank.common.ProtocolLimits;
import top.mc100.qdbank.common.packet.*;
import top.mc100.qdbank.common.dto.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class PacketCodec {
    private PacketCodec() {}

    static void encode(Packet packet, ByteBuf out) {
        switch (packet) {
            case HandshakePacket p -> { out.writeByte(1); string(out,p.clientId()); string(out,p.secretKey()); string(out,p.version()); }
            case HandshakeResultPacket p -> { out.writeByte(2); out.writeBoolean(p.accepted()); string(out,p.serverVersion()); string(out,p.message()); }
            case PingPacket p -> { out.writeByte(3); out.writeLong(p.timestamp()); }
            case PongPacket p -> { out.writeByte(4); out.writeLong(p.timestamp()); }
            case AccountRequestPacket p -> { out.writeByte(5); out.writeLong(p.requestId()); out.writeByte(p.operation().ordinal()); string(out,p.playerUuid()); string(out,p.playerName()); string(out,p.accountName()); out.writeBoolean(p.admin()); context(out,p.context()); }
            case AccountResponsePacket p -> {
                out.writeByte(6); out.writeLong(p.requestId()); out.writeBoolean(p.success()); string(out,p.message());
                string(out,p.playerUuid()); string(out,p.accountName()); strings(out,p.accounts()); bytes(out,p.content());
                out.writeLong(p.revision()); out.writeBoolean(p.readOnly());
            }
            case ContainerUpdatePacket p -> { out.writeByte(7); out.writeLong(p.requestId()); string(out,p.playerUuid()); string(out,p.accountName()); out.writeLong(p.expectedRevision()); bytes(out,p.content()); string(out,p.itemDetail()); context(out,p.context()); }
            case ContainerSyncPacket p -> { out.writeByte(8); out.writeLong(p.requestId()); string(out,p.playerUuid()); string(out,p.accountName()); out.writeLong(p.revision()); bytes(out,p.content()); out.writeBoolean(p.accepted()); string(out,p.message()); }
            case AccountClosePacket p -> { out.writeByte(9); string(out,p.playerUuid()); string(out,p.accountName()); context(out,p.context()); }
            case AuditLookupPacket p -> { out.writeByte(10); out.writeLong(p.requestId()); string(out,p.playerUuid()); out.writeLong(p.sinceEpochMillis());
                string(out,p.action());string(out,p.include());string(out,p.exclude());out.writeInt(p.page());out.writeInt(p.pageSize()); }
            case AuditResultPacket p -> { out.writeByte(11); out.writeLong(p.requestId());out.writeInt(p.page());out.writeInt(p.totalPages());entries(out,p.entries()); }
            case ErrorPacket p -> { out.writeByte(12); out.writeLong(p.requestId()); string(out,p.message()); }
            case RollbackPacket p -> { out.writeByte(13); out.writeLong(p.requestId()); string(out,p.playerUuid()); out.writeLong(p.sinceEpochMillis());
                string(out,p.action());string(out,p.include());string(out,p.exclude());context(out,p.context()); }
            case RollbackResultPacket p -> { out.writeByte(14); out.writeLong(p.requestId()); out.writeBoolean(p.success()); out.writeInt(p.restoredAccounts()); string(out,p.message()); }
            case DirectoryRequestPacket p -> { out.writeByte(15); out.writeLong(p.requestId()); string(out,p.playerUuid()); }
            case DirectoryResponsePacket p -> {
                out.writeByte(16);out.writeLong(p.requestId());out.writeInt(p.players().size());
                for(PlayerAccounts player:p.players()){string(out,player.uuid());string(out,player.playerName());strings(out,player.accounts());}
            }
        }
    }

    static Packet decode(ByteBuf in) {
        return switch (in.readUnsignedByte()) {
            case 1 -> new HandshakePacket(string(in), string(in), string(in));
            case 2 -> new HandshakeResultPacket(in.readBoolean(), string(in), string(in));
            case 3 -> new PingPacket(in.readLong());
            case 4 -> new PongPacket(in.readLong());
            case 5 -> new AccountRequestPacket(in.readLong(), AccountRequestPacket.Operation.values()[in.readUnsignedByte()], string(in), string(in), string(in), in.readBoolean(), context(in));
            case 6 -> new AccountResponsePacket(in.readLong(), in.readBoolean(), string(in), string(in), string(in), strings(in), bytes(in), in.readLong(), in.readBoolean());
            case 7 -> new ContainerUpdatePacket(in.readLong(), string(in), string(in), in.readLong(), bytes(in), string(in), context(in));
            case 8 -> new ContainerSyncPacket(in.readLong(), string(in), string(in), in.readLong(), bytes(in), in.readBoolean(), string(in));
            case 9 -> new AccountClosePacket(string(in), string(in), context(in));
            case 10 -> new AuditLookupPacket(in.readLong(),string(in),in.readLong(),string(in),string(in),string(in),in.readInt(),in.readInt());
            case 11 -> new AuditResultPacket(in.readLong(),in.readInt(),in.readInt(),entries(in));
            case 12 -> new ErrorPacket(in.readLong(), string(in));
            case 13 -> new RollbackPacket(in.readLong(),string(in),in.readLong(),string(in),string(in),string(in),context(in));
            case 14 -> new RollbackResultPacket(in.readLong(),in.readBoolean(),in.readInt(),string(in));
            case 15 -> new DirectoryRequestPacket(in.readLong(),string(in));
            case 16 -> {
                long id=in.readLong();int size=in.readInt();if(size<0||size>100000)throw new IllegalArgumentException("Invalid directory size");
                List<PlayerAccounts> players=new ArrayList<>(size);
                for(int i=0;i<size;i++)players.add(new PlayerAccounts(string(in),string(in),strings(in)));
                yield new DirectoryResponsePacket(id,players);
            }
            default -> throw new IllegalArgumentException("Unknown packet type");
        };
    }

    private static void string(ByteBuf out, String value) {
        byte[] data = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (data.length > ProtocolLimits.MAX_SIZE_BYTES) throw new IllegalArgumentException("String too large");
        out.writeInt(data.length).writeBytes(data);
    }

    private static String string(ByteBuf in) {
        int length = length(in);
        return in.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    private static void bytes(ByteBuf out, byte[] value) {
        byte[] data = value == null ? new byte[0] : value;
        if (data.length > ProtocolLimits.MAX_SIZE_BYTES) throw new IllegalArgumentException("Payload too large");
        out.writeInt(data.length).writeBytes(data);
    }

    private static byte[] bytes(ByteBuf in) {
        int length = length(in);
        byte[] data = new byte[length];
        in.readBytes(data);
        return data;
    }

    private static void strings(ByteBuf out, List<String> values) {
        out.writeInt(values.size());
        values.forEach(v -> string(out, v));
    }

    private static List<String> strings(ByteBuf in) {
        int size = length(in);
        if (size > 100_000) throw new IllegalArgumentException("List too large");
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(string(in));
        return values;
    }

    private static int length(ByteBuf in) {
        int length = in.readInt();
        if (length < 0 || length > in.readableBytes()) throw new IllegalArgumentException("Invalid field length");
        return length;
    }

    private static void context(ByteBuf out,OperationContext context){
        OperationContext value=context==null?new OperationContext("CONSOLE","CONSOLE","UNKNOWN",0,0,0,"unknown"):context;
        string(out,value.actorUuid());string(out,value.actorName());string(out,value.world());
        out.writeDouble(value.x());out.writeDouble(value.y());out.writeDouble(value.z());string(out,value.serverId());
    }
    private static OperationContext context(ByteBuf in){
        return new OperationContext(string(in),string(in),string(in),in.readDouble(),in.readDouble(),in.readDouble(),string(in));
    }
    private static void entries(ByteBuf out,List<AuditEntry> entries){
        out.writeInt(entries.size());
        for(AuditEntry entry:entries){
            string(out,entry.timestamp());string(out,entry.accountName());out.writeByte(entry.action().ordinal());
            out.writeInt(entry.changes().size());
            for(AuditItemChange change:entry.changes()){
                out.writeInt(change.slot());out.writeInt(change.amount());string(out,change.material());bytes(out,change.itemBytes());
                bytes(out,change.beforeItem());bytes(out,change.afterItem());
            }
            context(out,entry.context());
        }
    }
    private static List<AuditEntry> entries(ByteBuf in){
        int size=in.readInt();if(size<0||size>1000)throw new IllegalArgumentException("Invalid audit count");
        List<AuditEntry> result=new ArrayList<>(size);
        for(int i=0;i<size;i++){
            String timestamp=string(in),account=string(in);ActionType action=ActionType.values()[in.readUnsignedByte()];
            int count=in.readInt();if(count<0||count>500)throw new IllegalArgumentException("Invalid item count");
            List<AuditItemChange> changes=new ArrayList<>(count);
            for(int j=0;j<count;j++)changes.add(new AuditItemChange(in.readInt(),in.readInt(),string(in),bytes(in),bytes(in),bytes(in)));
            result.add(new AuditEntry(timestamp,account,action,changes,context(in)));
        }
        return result;
    }
}

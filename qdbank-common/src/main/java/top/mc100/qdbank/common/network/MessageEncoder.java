package top.mc100.qdbank.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import top.mc100.qdbank.common.packet.Packet;

public final class MessageEncoder extends MessageToByteEncoder<Packet> {
    @Override
    protected void encode(ChannelHandlerContext context, Packet packet, ByteBuf out) {
        PacketCodec.encode(packet, out);
    }
}

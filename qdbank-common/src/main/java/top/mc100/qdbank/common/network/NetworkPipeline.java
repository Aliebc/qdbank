package top.mc100.qdbank.common.network;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import top.mc100.qdbank.common.ProtocolLimits;

public final class NetworkPipeline {
    private NetworkPipeline() {}

    public static void install(ChannelPipeline pipeline) {
        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(ProtocolLimits.MAX_SIZE_BYTES, 0, 4, 0, 4));
        pipeline.addLast("messageDecoder", new MessageDecoder());
        pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
        pipeline.addLast("messageEncoder", new MessageEncoder());
    }
}

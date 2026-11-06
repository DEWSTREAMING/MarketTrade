package com.markettrade.net;

import com.markettrade.MarketTrade;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HelloPayload(int protocolVersion) implements CustomPacketPayload {
    public static final Type<HelloPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MarketTrade.MODID, "hello"));

    public static final StreamCodec<ByteBuf, HelloPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            HelloPayload::protocolVersion,
            HelloPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HelloPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                if (payload.protocolVersion() == ModNetworking.PROTOCOL_VERSION) {
                    ClientTracker.recordHello(context.player().getUUID());
                }
            }
        });
    }
}

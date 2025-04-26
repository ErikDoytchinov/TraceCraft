package eu.doytchinov.tracecraft.net;

import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.net.packet.ClientMetricsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

public final class NetworkHandler {
    private static final int PROTO = 1;

    public static SimpleChannel CHANNEL;

    private NetworkHandler() {}

    public static void init() {
        if (CHANNEL != null) return;

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TraceCraft.MODID, "main");

        CHANNEL = ChannelBuilder
                .named(id)
                .networkProtocolVersion(PROTO)
                .acceptedVersions((status, v) -> v == PROTO)
                .simpleChannel();

        registerPackets();
    }

    private static void registerPackets() {
        int idx = 0;
        CHANNEL.messageBuilder(ClientMetricsPacket.class, idx++)
                .encoder(ClientMetricsPacket::encode)
                .decoder(ClientMetricsPacket::decode)
                .consumerMainThread((pkt, ctx) -> {
                    if (ctx.getSender() != null) {
                        pkt.handle(ctx.getSender());
                    }
                })
                .add();
    }
}
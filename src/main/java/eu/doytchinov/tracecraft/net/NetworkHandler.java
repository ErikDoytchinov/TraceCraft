package eu.doytchinov.tracecraft.net;

import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.net.packet.ClientMetricsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

public final class NetworkHandler {
    private static final int PROTO = 1;

    public static SimpleChannel CHANNEL;

    private NetworkHandler() {
    }

    public static void init() {
        if (CHANNEL != null)
            return;

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(TraceCraft.MODID, "main");

        CHANNEL = ChannelBuilder
                .named(id)
                .networkProtocolVersion(PROTO)
                .acceptedVersions((status, v) -> {
                    // allow server to accept clients without the mod (version 0)
                    // allow clients to connect to servers with the mod (version PROTO)
                    // allow matching versions (both have PROTO)
                    return v == PROTO || v == 0;
                })
                .simpleChannel();

        registerPackets();
    }

    private static void registerPackets() {
        int idx = 0;
        CHANNEL.messageBuilder(ClientMetricsPacket.class, idx++)
                .encoder(ClientMetricsPacket::encode)
                .decoder(ClientMetricsPacket::decode)
                .consumerMainThread((pkt, ctx) -> {
                    // only handle the packet if we have a valid sender (server-side)
                    if (ctx.getSender() != null) {
                        try {
                            pkt.handle(ctx.getSender());
                        } catch (Exception e) {
                            com.mojang.logging.LogUtils.getLogger().warn(
                                    "Failed to handle client metrics packet from player {}: {}",
                                    ctx.getSender().getUUID(), e.getMessage());
                        }
                    }
                })
                .add();
    }
}
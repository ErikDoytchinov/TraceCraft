package eu.doytchinov.tracecraft.client;

import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.net.NetworkHandler;
import eu.doytchinov.tracecraft.net.packet.ClientMetricsPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = TraceCraft.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientHooks {

    private static long lastSent = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (Minecraft.getInstance().getConnection() == null) { return; } // not connected yet
        if (e.phase != TickEvent.Phase.END) return;

        long now = System.currentTimeMillis();
        if (now - lastSent < 5_000) return;      // send once per second
        lastSent = now;

        int   fps = Minecraft.getInstance().getFps();
        long  mem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        NetworkHandler.CHANNEL
                .send(new ClientMetricsPacket(fps, mem), PacketDistributor.SERVER.noArg());
    }
}

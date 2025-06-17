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

import java.util.Objects;

@Mod.EventBusSubscriber(modid = TraceCraft.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientHooks {

    private static long lastSent = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (Minecraft.getInstance().getConnection() == null)
            return;
        if (e.phase != TickEvent.Phase.END)
            return;

        long now = System.currentTimeMillis();
        if (now - lastSent < 5_000)
            return;
        lastSent = now;

        if (NetworkHandler.CHANNEL == null)
            return;

        try {
            int fps = Minecraft.getInstance().getFps();
            long mem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long ping = Objects.requireNonNull(Minecraft.getInstance().getConnection().getServerData()).ping;

            ClientMetricsPacket packet = new ClientMetricsPacket(fps, mem, ping);
            NetworkHandler.CHANNEL.send(packet, PacketDistributor.SERVER.noArg());
        } catch (IllegalStateException ex) {
            // server doesn't support our packets
            // this is expected for servers without the mod
        } catch (Exception ex) {
            System.err.println("TraceCraft: Unexpected error sending client metrics: " + ex.getMessage());
        }
    }
}

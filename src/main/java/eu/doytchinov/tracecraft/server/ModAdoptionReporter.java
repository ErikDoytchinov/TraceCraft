package eu.doytchinov.tracecraft.server;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.events.Event;
import eu.doytchinov.tracecraft.util.ClientModDetection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TraceCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
public class ModAdoptionReporter {

    private static long lastReportTime = System.currentTimeMillis();
    private static final long REPORT_INTERVAL_MS = 300_000L; // 5 minutes

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        MinecraftServer server = event.getServer();
        if (server == null)
            return;

        long now = System.currentTimeMillis();
        if (now - lastReportTime < REPORT_INTERVAL_MS)
            return;

        lastReportTime = now;

        int totalPlayers = server.getPlayerList().getPlayerCount();
        int playersWithMod = 0;
        int playersWithoutMod = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ClientModDetection.hasClientMod(player)) {
                playersWithMod++;
            } else {
                playersWithoutMod++;
            }
        }

        if (totalPlayers > 0) {
            double adoptionRate = (playersWithMod * 100.0) / totalPlayers;
            LogUtils.getLogger().info("TraceCraft Client Adoption: {}/{} players have the client mod ({:.1f}%)",
                    playersWithMod, totalPlayers, adoptionRate);

            JsonObject adoptionData = new JsonObject();
            adoptionData.addProperty("total_players", totalPlayers);
            adoptionData.addProperty("players_with_mod", playersWithMod);
            adoptionData.addProperty("players_without_mod", playersWithoutMod);
            adoptionData.addProperty("adoption_rate_percent", adoptionRate);
            Event.sendEvent("mod_adoption", adoptionData);
        }
    }
}

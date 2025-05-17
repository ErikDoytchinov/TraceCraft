package eu.doytchinov.tracecraft.server;

import com.google.gson.JsonObject;
import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.events.Event;
import eu.doytchinov.tracecraft.util.ChunkUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = TraceCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
public final class TimedEventManager {
    private static long nextPlayerCountEvent = System.currentTimeMillis() + 60_000L;
    private static long nextChunkMarginEvent = System.currentTimeMillis() + 10_000L;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        long now = System.currentTimeMillis();

        if (now >= nextPlayerCountEvent) {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();

            JsonObject o = new JsonObject();
            o.addProperty("count", players.size());

            TraceCraft.QUEUE.addEvent(new Event(o, "player_count"));
            nextPlayerCountEvent = now + 60_000L;
        }

        if (now >= nextChunkMarginEvent) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerLevel level = p.serverLevel(); // shortcut

                int dChunks = ChunkUtils.distanceToUnloaded(p, level);

                JsonObject o = new JsonObject();
                o.addProperty("player", p.getUUID().toString());
                o.addProperty("distance_chunks", dChunks);
                o.addProperty("distance_blocks", dChunks * 16);

                TraceCraft.QUEUE.addEvent(new Event(o, "player_chunk_margin"));
            }
            nextChunkMarginEvent = now + 10_000L;
        }
    }
}

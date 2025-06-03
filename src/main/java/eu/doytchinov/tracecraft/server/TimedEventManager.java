package eu.doytchinov.tracecraft.server;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.events.Event;
import eu.doytchinov.tracecraft.util.ChunkUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOutboundBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.Connection;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TraceCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
public final class TimedEventManager {
    private static long nextPlayerCountEvent = System.currentTimeMillis() + 60_000L;
    private static long nextChunkMarginEvent = System.currentTimeMillis() + 10_000L;
    private static long nextQueueMetricsEvent = System.currentTimeMillis() + 10_000L;
    private static long nextSystemMetricsEvent = System.currentTimeMillis() + 10_000L;
    private static long nextEntityCountEvent = System.currentTimeMillis() + 5_000L;

    private static final long METRICS_INTERVAL_SECONDS = 10L;
    private static final long METRICS_INTERVAL_MS = METRICS_INTERVAL_SECONDS * 1000L;

    private static final HashMap<UUID, Integer> lastTotalSentPackets = new HashMap<>();
    private static final HashMap<UUID, Long> lastMetricsTime = new HashMap<>();

    private static Field sentPacketsField;

    static {
        try {
            sentPacketsField = Connection.class.getDeclaredField("sentPackets");
            sentPacketsField.setAccessible(true);
        } catch (Throwable t) {
            LogUtils.getLogger().error(
                    "[TraceCraft] CRITICAL: Unexpected Throwable during reflection field initialization for 'sentPackets'. PPS metrics will NOT be collected.",
                    t);
            sentPacketsField = null;
        }
    }

    public static int getPendingMessageCount(ServerPlayer player) {
        Connection conn = player.connection.getConnection();
        Channel channel = conn.channel();

        ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
        if (buffer == null) {
            return 0;
        }
        return buffer.size();
    }

    public static long getPendingByteCount(ServerPlayer player) {
        Connection conn = player.connection.getConnection();
        Channel channel = conn.channel();

        ChannelOutboundBuffer buffer = channel.unsafe().outboundBuffer();
        if (buffer == null) {
            return 0L;
        }
        return buffer.totalPendingWriteBytes();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        MinecraftServer server = event.getServer();
        if (server == null)
            return;

        long now = System.currentTimeMillis();

        if (now >= nextPlayerCountEvent) {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();

            JsonObject o = new JsonObject();
            o.addProperty("count", players.size());

            TraceCraft.QUEUE.addEvent(new Event(o, "player_count"));
            nextPlayerCountEvent = now + 60_000L;
        }

        if (now >= nextEntityCountEvent) {
            for (ServerLevel level : server.getAllLevels()) {
                JsonObject levelMetrics = new JsonObject();
                levelMetrics.addProperty("level_name", level.dimension().location().toString());
                HashMap<String, Integer> entityCounts = new HashMap<>();
                int totalEntities = 0;
                for (Entity entity : level.getAllEntities()) {
                    String entityTypeName = entity.getType().toShortString();
                    entityCounts.put(entityTypeName, entityCounts.getOrDefault(entityTypeName, 0) + 1);
                    totalEntities++;
                }
                levelMetrics.addProperty("total_entities", totalEntities);
                JsonObject entityTypeCounts = new JsonObject();
                for (String entityType : entityCounts.keySet()) {
                    entityTypeCounts.addProperty(entityType, entityCounts.get(entityType));
                }
                levelMetrics.add("entity_types", entityTypeCounts);
                TraceCraft.QUEUE.addEvent(new Event(levelMetrics, "entity_count"));
            }
            nextEntityCountEvent = now + 5_000L; // Schedule next event
        }

        if (now >= nextChunkMarginEvent) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerLevel level = p.serverLevel();

                int dChunks = ChunkUtils.distanceToUnloaded(p, level);

                JsonObject o = new JsonObject();
                o.addProperty("player", p.getUUID().toString());
                o.addProperty("distance_chunks", dChunks);
                o.addProperty("distance_blocks", dChunks * 16);

                TraceCraft.QUEUE.addEvent(new Event(o, "player_chunk_margin"));
            }
            nextChunkMarginEvent = now + 10_000L;
        }

        if (now >= nextQueueMetricsEvent) {
            if (sentPacketsField == null) {
                nextQueueMetricsEvent = now + METRICS_INTERVAL_MS;
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Connection conn = player.connection.getConnection();
                UUID playerUUID = player.getUUID();

                long currentTime = System.currentTimeMillis();
                long lastTime = lastMetricsTime.getOrDefault(playerUUID,
                        currentTime - METRICS_INTERVAL_MS);
                long timeDiffSeconds = (currentTime - lastTime) / 1000;

                if (timeDiffSeconds <= 0)
                    timeDiffSeconds = 1;

                int totalSentPacketsNow = 0;

                try {
                    totalSentPacketsNow = sentPacketsField.getInt(conn);
                } catch (IllegalAccessException e) {
                    LogUtils.getLogger().error("[TraceCraft] Failed to get sent packets for player " + playerUUID, e);
                    continue;
                }

                int prevTotalSentPackets = lastTotalSentPackets.getOrDefault(playerUUID, totalSentPacketsNow);

                int packetsSentInInterval = totalSentPacketsNow - prevTotalSentPackets;

                if (packetsSentInInterval < 0)
                    packetsSentInInterval = 0;

                double pps = (double) packetsSentInInterval / timeDiffSeconds;

                JsonObject o = new JsonObject();
                o.addProperty("player", playerUUID.toString());
                o.addProperty("queued_messages", getPendingMessageCount(player));
                o.addProperty("queued_bytes", getPendingByteCount(player));

                o.addProperty("packets_per_second", pps);
                o.addProperty("average_packet_size_bytes",
                        getPendingByteCount(player) / Math.max(1, getPendingMessageCount(player))); // TOOD: Verify this
                                                                                                    // is valid way of
                                                                                                    // calculating this
                                                                                                    // metric
                TraceCraft.QUEUE.addEvent(new Event(o, "player_network_stats"));

                lastTotalSentPackets.put(playerUUID, totalSentPacketsNow);
                lastMetricsTime.put(playerUUID, currentTime);
            }
            nextQueueMetricsEvent = now + METRICS_INTERVAL_MS;
        }

        if (now >= nextSystemMetricsEvent) {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long allocatedMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = allocatedMemory - freeMemory;

            JsonObject systemMetrics = new JsonObject();
            systemMetrics.addProperty("max_heap_mb", maxMemory / (1024 * 1024));
            systemMetrics.addProperty("allocated_heap_mb", allocatedMemory / (1024 * 1024));
            systemMetrics.addProperty("used_heap_mb", usedMemory / (1024 * 1024));
            systemMetrics.addProperty("free_heap_mb", freeMemory / (1024 * 1024));
            systemMetrics.addProperty("thread_count", Thread.activeCount());

            TraceCraft.QUEUE.addEvent(new Event(systemMetrics, "system_metrics"));
            nextSystemMetricsEvent = now + METRICS_INTERVAL_MS;
        }
    }
}
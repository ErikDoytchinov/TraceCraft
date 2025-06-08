package eu.doytchinov.tracecraft.server;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import eu.doytchinov.tracecraft.events.Event;

public class ServerMetricsHandler {

    private static long lastSampleMs = System.currentTimeMillis();
    private static long lastSampleTick = 0L;
    private static long tickStartNanos;

    public static void handleTickForTPS(TickEvent.ServerTickEvent event, MinecraftServer server) {
        if (event.phase != TickEvent.Phase.END)
            return;

        long nowMs = System.currentTimeMillis();
        long elapsedMs = nowMs - lastSampleMs;

        if (elapsedMs >= 5000L) {
            long tickDelta = server.getTickCount() - lastSampleTick;
            double tps = (tickDelta * 1000.0) / elapsedMs;

            JsonObject o = new JsonObject();
            o.addProperty("value", tps);
            Event.sendEvent("tps", o);

            lastSampleMs = nowMs;
            lastSampleTick = server.getTickCount();
        }
    }

    public static void handleTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;
        tickStartNanos = System.nanoTime();
    }

    public static void handleTickEndMetrics(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        long durationNanos = System.nanoTime() - tickStartNanos;
        double durationMs = durationNanos / 1_000_000.0;
        double expectedMs = 50.0;
        double isr = durationMs / expectedMs;

        JsonObject o = new JsonObject();
        o.addProperty("duration_ms", durationMs);
        o.addProperty("isr", isr);
        Event.sendEvent("tick_metrics", o);
    }
}

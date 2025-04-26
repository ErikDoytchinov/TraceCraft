package eu.doytchinov.tracecraft.server;

import com.google.gson.JsonObject;
import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.events.Event;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = TraceCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
public class TimedEventManager {
    private static long nextPlayerCountEvent = System.currentTimeMillis() + 60000; // 1 minute from start

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        MinecraftServer server = event.getServer();
        if (server == null)
            return;

        // check if it's time for a player count event to be added
        long now = System.currentTimeMillis();
        if (now >= nextPlayerCountEvent) {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            int playerCount = players.size();
            JsonObject o = new JsonObject();
            o.addProperty("event", "player_count");
            o.addProperty("count", playerCount);
            o.addProperty("ts", now);
            TraceCraft.QUEUE.addEvent(new Event(o));
            nextPlayerCountEvent = now + 60000;
        }
    }
}

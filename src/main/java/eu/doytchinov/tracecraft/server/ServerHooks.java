package eu.doytchinov.tracecraft.server;

import com.google.gson.JsonObject;
import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.events.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TraceCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
public class ServerHooks {
    private static long lastStart = System.nanoTime();

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p))
            return;
        BlockPos pos = e.getPos();
        JsonObject o = new JsonObject();
        o.addProperty("event", "place");
        o.addProperty("player", p.getUUID().toString());
        o.addProperty("block", e.getState().getBlock().toString());
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        o.addProperty("ts", System.currentTimeMillis());
        TraceCraft.QUEUE.addEvent(new Event(o));
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent e) {
        JsonObject o = new JsonObject();
        o.addProperty("event", "login");
        o.addProperty("player", e.getEntity().getUUID().toString());
        o.addProperty("ts", System.currentTimeMillis());
        TraceCraft.QUEUE.addEvent(new Event(o));

        // since the player joined here we can send them a
        // message saying we are collecting their data
        if (e.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "TraceCraft: We are collecting your data for research purposes. If you do not want this, please leave the server."
            ));
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END)
            return;
        long now = System.nanoTime();
        long dur = now - lastStart;
        lastStart = now;
        if (e.getServer().getTickCount() % 20 == 0) {
            double tps = 1e9 / dur;
            JsonObject o = new JsonObject();
            o.addProperty("event", "tps");
            o.addProperty("value", tps);
            o.addProperty("ts", System.currentTimeMillis());
            TraceCraft.QUEUE.addEvent(new Event(o));
        }
    }
}
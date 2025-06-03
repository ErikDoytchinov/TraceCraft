package eu.doytchinov.tracecraft.server;

import com.google.gson.JsonObject;
import eu.doytchinov.tracecraft.TraceCraft;
import eu.doytchinov.tracecraft.events.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.ComparatorBlock;

@Mod.EventBusSubscriber(modid = TraceCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
public final class ServerHooks {
    private static long lastSampleMs = System.currentTimeMillis();
    private static long lastSampleTick = 0L;
    private static long tickStartNanos;

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p))
            return;
        sendBlockEntityEvent(p, e.getPos(), e.getState(), "block_place");
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p))
            return;
        sendBlockEntityEvent(p, e.getPos(), e.getState(), "block_break");
    }

    private static void sendBlockEntityEvent(ServerPlayer p,
                                             BlockPos pos,
                                             BlockState state,
                                             String type) {
        JsonObject o = new JsonObject();
        o.addProperty("player", p.getUUID().toString());
        o.addProperty("block", state.getBlock().toString());
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        TraceCraft.QUEUE.addEvent(new Event(o, type));
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        JsonObject o = new JsonObject();
        o.addProperty("player", e.getEntity().getUUID().toString());
        TraceCraft.QUEUE.addEvent(new Event(o, "login"));

        if (e.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "TraceCraft: We are collecting your gameplay data for research. " +
                            "If you prefer not to participate, please disconnect."));
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        JsonObject o = new JsonObject();
        o.addProperty("player", e.getEntity().getUUID().toString());
        TraceCraft.QUEUE.addEvent(new Event(o, "logout"));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        // run exactly once per server tick
        if (event.phase != TickEvent.Phase.END)
            return;

        MinecraftServer server = event.getServer();
        long nowMs = System.currentTimeMillis();
        long elapsedMs = nowMs - lastSampleMs;

        if (elapsedMs >= 5000L) {
            long tickDelta = server.getTickCount() - lastSampleTick;
            double tps = (tickDelta * 1000.0) / elapsedMs;

            JsonObject o = new JsonObject();
            o.addProperty("value", tps);
            TraceCraft.QUEUE.addEvent(new Event(o, "tps"));

            lastSampleMs = nowMs;
            lastSampleTick = server.getTickCount();
        }
    }

    @SubscribeEvent
    public static void onTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;
        tickStartNanos = System.nanoTime();
    }

    @SubscribeEvent
    public static void onTickEndMetrics(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        long durationNanos = System.nanoTime() - tickStartNanos;
        double durationMs = durationNanos / 1_000_000.0;
        double expectedMs = 50.0;
        double isr = durationMs / expectedMs;

        JsonObject o = new JsonObject();
        o.addProperty("duration_ms", durationMs);
        o.addProperty("isr", isr);
        TraceCraft.QUEUE.addEvent(new Event(o, "tick_metrics"));
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent e) {
        BlockState state = e.getState();
        if (isPhysicsRelevant(state)) {
            JsonObject o = new JsonObject();
            BlockPos pos = e.getPos();
            o.addProperty("block", state.getBlock().toString());
            o.addProperty("x", pos.getX());
            o.addProperty("y", pos.getY());
            o.addProperty("z", pos.getZ());

            TraceCraft.QUEUE.addEvent(new Event(o, "physics_event"));
        }
    }

    private static boolean isPhysicsRelevant(BlockState state) {
        return state.getBlock() instanceof FallingBlock ||
               state.getBlock() instanceof PistonBaseBlock ||
               state.getBlock() instanceof MovingPistonBlock ||
               state.getBlock() instanceof LiquidBlock ||
               state.getBlock() instanceof RedStoneWireBlock ||
               state.getBlock() instanceof ObserverBlock ||
               state.getBlock() instanceof RepeaterBlock ||
               state.getBlock() instanceof ComparatorBlock;
    }
}

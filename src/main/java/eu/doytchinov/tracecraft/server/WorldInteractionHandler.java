package eu.doytchinov.tracecraft.server;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import eu.doytchinov.tracecraft.events.Event;

public class WorldInteractionHandler {

    private static long chunkGenStartNanos;

    private static void sendBlockEntityEvent(ServerPlayer p, BlockPos pos, BlockState state, String type) {
        JsonObject o = Event.createPlayerPayload(p.getUUID());
        o.addProperty("block", state.getBlock().toString());
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        Event.sendEvent(type, o);
        // mark player active on block interaction
        PlayerSessionData.getLastActiveMs().put(p.getUUID(), System.currentTimeMillis());
    }

    public static void handleBlockPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p))
            return;
        sendBlockEntityEvent(p, e.getPos(), e.getState(), "block_place");
    }

    public static void handleBlockBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p))
            return;
        sendBlockEntityEvent(p, e.getPos(), e.getState(), "block_break");
    }

    public static void handleLightUpdate(LevelEvent.Load event) {
        if (event.getLevel() == null) {
            return;
        }
        JsonObject o = new JsonObject();
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            o.addProperty("world", serverLevel.dimension().location().toString());
        }
        Event.sendEvent("lighting_update_world_load", o);
    }

    public static void handleChunkLoad(ChunkEvent.Load event) {
        chunkGenStartNanos = System.nanoTime();
    }

    public static void handleChunkGenerated(ChunkEvent.Load event) {
        if (chunkGenStartNanos == 0) {
            return;
        }
        long durationNanos = System.nanoTime() - chunkGenStartNanos;
        double durationMs = durationNanos / 1_000_000.0;
        chunkGenStartNanos = 0;

        if (event.getLevel() == null || event.getChunk() == null) {
            return;
        }

        JsonObject o = new JsonObject();
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            o.addProperty("world", serverLevel.dimension().location().toString());
        }
        o.addProperty("chunk_x", event.getChunk().getPos().x);
        o.addProperty("chunk_z", event.getChunk().getPos().z);
        o.addProperty("generation_time_ms", durationMs);
        Event.sendEvent("chunk_generated", o);
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

    public static void handleNeighborNotify(BlockEvent.NeighborNotifyEvent e) {
        BlockState state = e.getState();
        if (isPhysicsRelevant(state)) {
            JsonObject o = new JsonObject();
            BlockPos pos = e.getPos();
            o.addProperty("block", state.getBlock().toString());
            o.addProperty("x", pos.getX());
            o.addProperty("y", pos.getY());
            o.addProperty("z", pos.getZ());
            Event.sendEvent("physics_event", o);
        }
    }
}

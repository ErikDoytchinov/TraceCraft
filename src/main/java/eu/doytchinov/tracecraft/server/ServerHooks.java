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
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;

@Mod.EventBusSubscriber(modid = TraceCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
public final class ServerHooks {
    private static final long AFK_THRESHOLD_MS = 300_000L; // 5 minutes
    private static final Map<UUID, Long> lastActiveMs = new HashMap<>();
    private static final Map<UUID, Long> idleTimeMs = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> biomeTime = new HashMap<>();
    private static final Map<UUID, BlockPos> lastSessionPos = new HashMap<>();
    private static final Map<UUID, Double> sessionDistance = new HashMap<>();
    private static long lastSampleMs = System.currentTimeMillis();
    private static long lastSampleTick = 0L;
    private static long tickStartNanos;
    private static long chunkGenStartNanos;
    private static long pathSampleIntervalMs = 1000L;
    private static long lastPathSampleMs = System.currentTimeMillis();

    /**
     * Sends an event with specified type and JSON payload.
     */
    private static void sendEvent(String type, JsonObject payload) {
        TraceCraft.QUEUE.addEvent(new Event(payload, type));
    }

    /**
     * Creates a basic JSON payload with player UUID.
     */
    private static JsonObject createPlayerPayload(UUID playerId) {
        JsonObject o = new JsonObject();
        o.addProperty("player", playerId.toString());
        return o;
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        sendBlockEntityEvent(p, e.getPos(), e.getState(), "block_place");
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p)) return;
        sendBlockEntityEvent(p, e.getPos(), e.getState(), "block_break");
    }

    private static void sendBlockEntityEvent(ServerPlayer p, BlockPos pos, BlockState state, String type) {
        JsonObject o = createPlayerPayload(p.getUUID());
        o.addProperty("block", state.getBlock().toString());
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        sendEvent(type, o);
        // mark player active on block interaction
        lastActiveMs.put(p.getUUID(), System.currentTimeMillis());
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        JsonObject o = createPlayerPayload(e.getEntity().getUUID());
        sendEvent("login", o);

        if (e.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("TraceCraft: We are collecting your gameplay data for research. " + "If you prefer not to participate, please disconnect."));
            // initialize session tracking
            UUID id = player.getUUID();
            long now = System.currentTimeMillis();
            lastSessionPos.put(id, player.blockPosition());
            sessionDistance.put(id, 0.0);
            biomeTime.put(id, new HashMap<>());
            // initialize AFK tracking
            lastActiveMs.put(id, now);
            idleTimeMs.put(id, 0L);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        // logout event
        sendEvent("logout", createPlayerPayload(id));

        // emit session distance
        JsonObject distancePayload = createPlayerPayload(id);
        distancePayload.addProperty("distance", sessionDistance.getOrDefault(id, 0.0));
        sendEvent("session_distance", distancePayload);

        // emit biome time per region
        for (var entry : biomeTime.getOrDefault(id, Map.of()).entrySet()) {
            JsonObject biomePayload = createPlayerPayload(id);
            biomePayload.addProperty("biome", entry.getKey());
            biomePayload.addProperty("duration_ms", entry.getValue());
            sendEvent("biome_time", biomePayload);
        }

        // cleanup session data
        lastSessionPos.remove(id);
        sessionDistance.remove(id);
        biomeTime.remove(id);

        // emit session idle time and cleanup AFK tracking
        JsonObject idlePayload = createPlayerPayload(id);
        idlePayload.addProperty("idle_ms", idleTimeMs.getOrDefault(id, 0L));
        sendEvent("session_idle", idlePayload);
        lastActiveMs.remove(id);
        idleTimeMs.remove(id);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        // run exactly once per server tick
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        long nowMs = System.currentTimeMillis();
        long elapsedMs = nowMs - lastSampleMs;

        if (elapsedMs >= 5000L) {
            long tickDelta = server.getTickCount() - lastSampleTick;
            double tps = (tickDelta * 1000.0) / elapsedMs;

            JsonObject o = new JsonObject();
            o.addProperty("value", tps);
            sendEvent("tps", o);

            lastSampleMs = nowMs;
            lastSampleTick = server.getTickCount();
        }
    }

    @SubscribeEvent
    public static void onTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        tickStartNanos = System.nanoTime();
    }

    @SubscribeEvent
    public static void onTickEndMetrics(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long durationNanos = System.nanoTime() - tickStartNanos;
        double durationMs = durationNanos / 1_000_000.0;
        double expectedMs = 50.0;
        double isr = durationMs / expectedMs;

        JsonObject o = new JsonObject();
        o.addProperty("duration_ms", durationMs);
        o.addProperty("isr", isr);
        sendEvent("tick_metrics", o);
    }

    @SubscribeEvent
    public static void onLightUpdate(LevelEvent.Load event) {
        if (event.getLevel() == null) {
            return;
        }
        // it's not possible to get the exact position of a lighting update from this
        // event
        // but we can log that a lighting update occurred in a specific world
        JsonObject o = new JsonObject();
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            o.addProperty("world", serverLevel.dimension().location().toString());
        }
        sendEvent("lighting_update_world_load", o);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        chunkGenStartNanos = System.nanoTime();
    }

    @SubscribeEvent
    public static void onChunkGenerated(ChunkEvent.Load event) {
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
        sendEvent("chunk_generated", o);
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

            sendEvent("physics_event", o);
        }
    }

    private static boolean isPhysicsRelevant(BlockState state) {
        return state.getBlock() instanceof FallingBlock || state.getBlock() instanceof PistonBaseBlock || state.getBlock() instanceof MovingPistonBlock || state.getBlock() instanceof LiquidBlock || state.getBlock() instanceof RedStoneWireBlock || state.getBlock() instanceof ObserverBlock || state.getBlock() instanceof RepeaterBlock || state.getBlock() instanceof ComparatorBlock;
    }

    @SubscribeEvent
    public static void samplePlayerPaths(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = System.currentTimeMillis();
        long delta = now - lastPathSampleMs;
        if (delta < pathSampleIntervalMs) return;
        lastPathSampleMs = now;
        MinecraftServer server = event.getServer();
        List<ServerPlayer> allPlayers = server.getPlayerList().getPlayers();
        for (ServerPlayer player : allPlayers) {
            UUID id = player.getUUID();
            BlockPos currentPos = player.blockPosition();
            // accumulate session distance
            BlockPos prev = lastSessionPos.get(id);
            boolean moved = false;
            if (prev != null) {
                double dx = currentPos.getX() - prev.getX();
                double dy = currentPos.getY() - prev.getY();
                double dz = currentPos.getZ() - prev.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                sessionDistance.put(id, sessionDistance.getOrDefault(id, 0.0) + dist);
                if (dist > 0.0) moved = true;
            }
            lastSessionPos.put(id, currentPos);
            // accumulate biome time
            ServerLevel lvl = player.serverLevel();
            Biome bm = lvl.getBiome(currentPos).value();
            String bid = ForgeRegistries.BIOMES.getKey(bm).toString();
            var map = biomeTime.get(id);
            if (map != null) {
                map.put(bid, map.getOrDefault(bid, 0L) + delta);
            }
            // AFK detection: if player moved recently, mark active; otherwise accumulate
            // idle
            long lastAct = lastActiveMs.getOrDefault(id, now);
            if (moved) {
                lastActiveMs.put(id, now);
            } else if (now - lastAct >= AFK_THRESHOLD_MS) {
                idleTimeMs.put(id, idleTimeMs.getOrDefault(id, 0L) + delta);
            }
            // emit path event
            JsonObject o = new JsonObject();
            o.addProperty("player", id.toString());
            o.addProperty("world", player.serverLevel().dimension().location().toString());
            o.addProperty("x", currentPos.getX());
            o.addProperty("y", currentPos.getY());
            o.addProperty("z", currentPos.getZ());
            sendEvent("player_path", o);
            double sumDist = 0.0;
            int count = 0;
            for (ServerPlayer other : allPlayers) {
                if (other == player) continue;
                BlockPos p2 = other.blockPosition();
                double dx = currentPos.getX() - p2.getX();
                double dy = currentPos.getY() - p2.getY();
                double dz = currentPos.getZ() - p2.getZ();
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                sumDist += d;
                count++;
            }
            double avg = count > 0 ? sumDist / count : 0.0;
            JsonObject soc = new JsonObject();
            soc.addProperty("player", id.toString());
            soc.addProperty("nearby_count", count);
            soc.addProperty("avg_distance", avg);
            sendEvent("social_proximity", soc);
        }
    }

    @SubscribeEvent
    public static void onItemUse(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        JsonObject o = new JsonObject();
        o.addProperty("player", p.getUUID().toString());
        o.addProperty("item", e.getItemStack().getItem().toString());
        sendEvent("item_use", o);
    }

    @SubscribeEvent
    public static void onCombatEvent(LivingHurtEvent event) {
        var target = event.getEntity();
        DamageSource src = event.getSource();
        var attacker = src.getEntity();
        JsonObject o = new JsonObject();
        o.addProperty("damage", event.getAmount());
        o.addProperty("target_type", target.getType().toShortString());
        if (attacker instanceof ServerPlayer p) {
            o.addProperty("attacker_player", p.getUUID().toString());
        } else if (attacker instanceof LivingEntity le) {
            o.addProperty("attacker_entity", le.getType().toShortString());
        }
        sendEvent("combat_event", o);
        // mark active on combat
        if (attacker instanceof ServerPlayer ap) lastActiveMs.put(ap.getUUID(), System.currentTimeMillis());
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        BlockPos pos = p.blockPosition();
        JsonObject o = new JsonObject();
        o.addProperty("player", p.getUUID().toString());
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        o.addProperty("cause", event.getSource().getMsgId());
        sendEvent("player_death", o);
    }
}

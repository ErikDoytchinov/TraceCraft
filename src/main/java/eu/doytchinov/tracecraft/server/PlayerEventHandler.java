package eu.doytchinov.tracecraft.server;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.TickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerEventHandler {

    private static final long AFK_THRESHOLD_MS = 300_000L; // 5 minutes
    private static long pathSampleIntervalMs = 1000L;
    private static long lastPathSampleMs = System.currentTimeMillis();

    public static void handleLogin(PlayerEvent.PlayerLoggedInEvent e) {
        UUID playerId = e.getEntity().getUUID();
        EventHelper.sendEvent("login", EventHelper.createPlayerPayload(playerId));

        if (e.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(net.minecraft.network.chat.Component
                    .literal("TraceCraft: We are collecting your gameplay data for research. "
                            + "If you prefer not to participate, please disconnect."));
            PlayerSessionData.initializePlayerData(playerId, player.blockPosition());
        }
    }

    public static void handleLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        EventHelper.sendEvent("logout", EventHelper.createPlayerPayload(id));

        JsonObject distancePayload = EventHelper.createPlayerPayload(id);
        distancePayload.addProperty("distance", PlayerSessionData.getSessionDistance().getOrDefault(id, 0.0));
        EventHelper.sendEvent("session_distance", distancePayload);

        for (var entry : PlayerSessionData.getBiomeTime().getOrDefault(id, Map.of()).entrySet()) {
            JsonObject biomePayload = EventHelper.createPlayerPayload(id);
            biomePayload.addProperty("biome", entry.getKey());
            biomePayload.addProperty("duration_ms", entry.getValue());
            EventHelper.sendEvent("biome_time", biomePayload);
        }

        JsonObject idlePayload = EventHelper.createPlayerPayload(id);
        idlePayload.addProperty("idle_ms", PlayerSessionData.getIdleTimeMs().getOrDefault(id, 0L));
        EventHelper.sendEvent("session_idle", idlePayload);

        PlayerSessionData.clearPlayerData(id);
    }

    public static void handleItemUse(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer p))
            return;
        JsonObject o = new JsonObject();
        o.addProperty("player", p.getUUID().toString());
        o.addProperty("item", e.getItemStack().getItem().toString());
        EventHelper.sendEvent("item_use", o);
        // Mark active on item use
        PlayerSessionData.getLastActiveMs().put(p.getUUID(), System.currentTimeMillis());
    }

    public static void handleCombatEvent(LivingHurtEvent event) {
        var target = event.getEntity();
        DamageSource src = event.getSource();
        var attacker = src.getEntity();
        JsonObject o = new JsonObject();
        o.addProperty("damage", event.getAmount());
        o.addProperty("target_type", target.getType().toShortString());
        if (attacker instanceof ServerPlayer p) {
            o.addProperty("attacker_player", p.getUUID().toString());
            // mark active on combat
            PlayerSessionData.getLastActiveMs().put(p.getUUID(), System.currentTimeMillis());
        } else if (attacker instanceof LivingEntity le) {
            o.addProperty("attacker_entity", le.getType().toShortString());
        }
        EventHelper.sendEvent("combat_event", o);
    }

    public static void handlePlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p))
            return;
        BlockPos pos = p.blockPosition();
        JsonObject o = new JsonObject();
        o.addProperty("player", p.getUUID().toString());
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        o.addProperty("cause", event.getSource().getMsgId());
        EventHelper.sendEvent("player_death", o);
    }

    public static void samplePlayerPathsAndProximity(TickEvent.ServerTickEvent event, MinecraftServer server) {
        if (event.phase != TickEvent.Phase.END)
            return;
        long now = System.currentTimeMillis();
        long delta = now - lastPathSampleMs;
        if (delta < pathSampleIntervalMs)
            return;
        lastPathSampleMs = now;

        List<ServerPlayer> allPlayers = server.getPlayerList().getPlayers();
        for (ServerPlayer player : allPlayers) {
            UUID id = player.getUUID();
            BlockPos currentPos = player.blockPosition();

            // Accumulate session distance
            BlockPos prevPos = PlayerSessionData.getLastSessionPos().get(id);
            boolean moved = false;
            if (prevPos != null) {
                double dx = currentPos.getX() - prevPos.getX();
                double dy = currentPos.getY() - prevPos.getY();
                double dz = currentPos.getZ() - prevPos.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                PlayerSessionData.getSessionDistance().put(id,
                        PlayerSessionData.getSessionDistance().getOrDefault(id, 0.0) + dist);
                if (dist > 0.0)
                    moved = true;
            }
            PlayerSessionData.getLastSessionPos().put(id, currentPos);

            // Accumulate biome time
            ServerLevel lvl = player.serverLevel();
            Biome bm = lvl.getBiome(currentPos).value();
            // Add null check for biome key
            net.minecraft.resources.ResourceLocation biomeKey = ForgeRegistries.BIOMES.getKey(bm);
            if (biomeKey != null) {
                String bid = biomeKey.toString();
                var playerBiomeTimes = PlayerSessionData.getBiomeTime().get(id);
                if (playerBiomeTimes != null) {
                    playerBiomeTimes.put(bid, playerBiomeTimes.getOrDefault(bid, 0L) + delta);
                }
            } else {
                // Log or handle the case where biome key is null, e.g., by skipping biome time
                // update for this tick
                // For now, we'll just skip it
            }

            // AFK detection
            long lastAct = PlayerSessionData.getLastActiveMs().getOrDefault(id, now);
            if (moved) {
                PlayerSessionData.getLastActiveMs().put(id, now);
            } else if (now - lastAct >= AFK_THRESHOLD_MS) {
                PlayerSessionData.getIdleTimeMs().put(id,
                        PlayerSessionData.getIdleTimeMs().getOrDefault(id, 0L) + delta);
            }

            // Emit path event
            JsonObject pathPayload = new JsonObject();
            pathPayload.addProperty("player", id.toString());
            pathPayload.addProperty("world", player.serverLevel().dimension().location().toString());
            pathPayload.addProperty("x", currentPos.getX());
            pathPayload.addProperty("y", currentPos.getY());
            pathPayload.addProperty("z", currentPos.getZ());
            EventHelper.sendEvent("player_path", pathPayload);

            // Social proximity
            double sumDist = 0.0;
            int count = 0;
            for (ServerPlayer other : allPlayers) {
                if (other == player)
                    continue;
                BlockPos p2 = other.blockPosition();
                double dx = currentPos.getX() - p2.getX();
                double dy = currentPos.getY() - p2.getY();
                double dz = currentPos.getZ() - p2.getZ();
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                sumDist += d;
                count++;
            }
            double avg = count > 0 ? sumDist / count : 0.0;
            JsonObject socialPayload = new JsonObject();
            socialPayload.addProperty("player", id.toString());
            socialPayload.addProperty("nearby_count", count);
            socialPayload.addProperty("avg_distance", avg);
            EventHelper.sendEvent("social_proximity", socialPayload);
        }
    }
}

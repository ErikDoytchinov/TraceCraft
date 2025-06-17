package eu.doytchinov.tracecraft.server;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import eu.doytchinov.tracecraft.events.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PlayerEventHandler {
    private static final long AFK_THRESHOLD_MS = 300_000L; // 5 minutes
    private static long pathSampleIntervalMs = 1000L;
    private static long lastPathSampleMs = System.currentTimeMillis();

    public static void handleLogin(PlayerEvent.PlayerLoggedInEvent e) {
        UUID playerId = e.getEntity().getUUID();
        Event.sendEvent("login", Event.createPlayerPayload(playerId));

        if (e.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(net.minecraft.network.chat.Component
                    .literal("TraceCraft: We are collecting server-side gameplay data for research. "
                            + "If you have the TraceCraft client mod, additional metrics will be collected. "
                            + "If you prefer not to participate, please disconnect."));

            PlayerSessionData.initializePlayerData(playerId, player.blockPosition());
            LogUtils.getLogger().info("Player {} logged in. Client mod status will be determined automatically.",
                    playerId);
        }
    }

    public static void handleLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        Event.sendEvent("logout", Event.createPlayerPayload(id));

        JsonObject distancePayload = Event.createPlayerPayload(id);
        distancePayload.addProperty("distance", PlayerSessionData.getSessionDistance().getOrDefault(id, 0.0));
        Event.sendEvent("session_distance", distancePayload);

        for (var entry : PlayerSessionData.getBiomeTime().getOrDefault(id, Map.of()).entrySet()) {
            JsonObject biomePayload = Event.createPlayerPayload(id);
            biomePayload.addProperty("biome", entry.getKey());
            biomePayload.addProperty("duration_ms", entry.getValue());
            Event.sendEvent("biome_time", biomePayload);
        }

        JsonObject idlePayload = Event.createPlayerPayload(id);
        idlePayload.addProperty("idle_ms", PlayerSessionData.getIdleTimeMs().getOrDefault(id, 0L));
        Event.sendEvent("session_idle", idlePayload);

        PlayerSessionData.clearPlayerData(id);
        eu.doytchinov.tracecraft.util.ClientModDetection.cleanupPlayer(id);

        LogUtils.getLogger().info("Player {} logged out and session data cleaned up.", id);
    }

    public static void handleItemUse(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer p))
            return;
        JsonObject o = new JsonObject();
        o.addProperty("player", p.getUUID().toString());
        o.addProperty("item", e.getItemStack().getItem().toString());
        Event.sendEvent("item_use", o);
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
        Event.sendEvent("combat_event", o);
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
        Event.sendEvent("player_death", o);
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

            ServerLevel lvl = player.serverLevel();
            Holder<Biome> biomeHolder = lvl.getBiome(currentPos);
            Optional<ResourceKey<Biome>> optKey = biomeHolder.unwrapKey();

            if (optKey.isPresent()) {
                ResourceLocation biomeRL = optKey.get().location();
                String bid = biomeRL.toString();
                var playerBiomeTimes = PlayerSessionData.getBiomeTime().get(id);
                if (playerBiomeTimes != null) {
                    long newTime = playerBiomeTimes.getOrDefault(bid, 0L) + delta;
                    playerBiomeTimes.put(bid, newTime);
                    LogUtils.getLogger().debug("Player {} biome time updated for {}: {}", id, bid, newTime);
                }
            } else {
                LogUtils.getLogger().warn("Biome holder has no registry key for player {} at {}", id, currentPos);
            }

            // AFK detection
            long lastAct = PlayerSessionData.getLastActiveMs().getOrDefault(id, now);
            if (moved) {
                PlayerSessionData.getLastActiveMs().put(id, now);
                LogUtils.getLogger().debug("Player {} marked active due to movement.", id);
            } else if (now - lastAct >= AFK_THRESHOLD_MS) {
                PlayerSessionData.getIdleTimeMs().put(id,
                        PlayerSessionData.getIdleTimeMs().getOrDefault(id, 0L) + delta);
                LogUtils.getLogger().debug("Player {} AFK time updated. Idle time: {}", id,
                        PlayerSessionData.getIdleTimeMs().get(id));
            }

            JsonObject pathPayload = new JsonObject();
            pathPayload.addProperty("player", id.toString());
            pathPayload.addProperty("world", player.serverLevel().dimension().location().toString());
            pathPayload.addProperty("x", currentPos.getX());
            pathPayload.addProperty("y", currentPos.getY());
            pathPayload.addProperty("z", currentPos.getZ());
            Event.sendEvent("player_path", pathPayload);
            LogUtils.getLogger().debug("Sent player_path event for player {}", id);

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
            Event.sendEvent("social_proximity", socialPayload);
            LogUtils.getLogger().debug("Sent social_proximity event for player {}. Nearby count: {}, Avg distance: {}",
                    id, count,
                    avg);
        }
    }
}

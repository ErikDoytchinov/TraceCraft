package eu.doytchinov.tracecraft.server;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerSessionData {
    static final Map<UUID, Long> lastActiveMs = new HashMap<>();
    static final Map<UUID, Long> idleTimeMs = new HashMap<>();
    static final Map<UUID, Map<String, Long>> biomeTime = new HashMap<>();
    static final Map<UUID, BlockPos> lastSessionPos = new HashMap<>();
    static final Map<UUID, Double> sessionDistance = new HashMap<>();

    public static void initializePlayerData(UUID playerId, BlockPos initialPos) {
        long now = System.currentTimeMillis();
        lastSessionPos.put(playerId, initialPos);
        sessionDistance.put(playerId, 0.0);
        biomeTime.put(playerId, new HashMap<>());
        lastActiveMs.put(playerId, now);
        idleTimeMs.put(playerId, 0L);
    }

    public static void clearPlayerData(UUID playerId) {
        lastSessionPos.remove(playerId);
        sessionDistance.remove(playerId);
        biomeTime.remove(playerId);
        lastActiveMs.remove(playerId);
        idleTimeMs.remove(playerId);
    }

    public static Map<UUID, Long> getLastActiveMs() {
        return lastActiveMs;
    }

    public static Map<UUID, Long> getIdleTimeMs() {
        return idleTimeMs;
    }

    public static Map<UUID, Map<String, Long>> getBiomeTime() {
        return biomeTime;
    }

    public static Map<UUID, BlockPos> getLastSessionPos() {
        return lastSessionPos;
    }

    public static Map<UUID, Double> getSessionDistance() {
        return sessionDistance;
    }
}

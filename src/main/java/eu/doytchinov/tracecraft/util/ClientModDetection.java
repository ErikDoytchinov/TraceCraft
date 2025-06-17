package eu.doytchinov.tracecraft.util;

import eu.doytchinov.tracecraft.TraceCraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClientModDetection {
    private static final Map<UUID, Boolean> clientModStatus = new HashMap<>();

    public static boolean hasClientMod(ServerPlayer player) {
        UUID playerId = player.getUUID();

        // if we've already determined the status, use cached result
        if (clientModStatus.containsKey(playerId)) {
            return clientModStatus.get(playerId);
        }

        clientModStatus.put(playerId, false);
        return false;
    }

    public static void markPlayerHasMod(ServerPlayer player) {
        clientModStatus.put(player.getUUID(), true);
    }

    public static void cleanupPlayer(UUID playerId) {
        clientModStatus.remove(playerId);
    }

    public static String getStatistics() {
        int totalPlayers = clientModStatus.size();
        long playersWithMod = clientModStatus.values().stream().mapToLong(hasMod -> hasMod ? 1 : 0).sum();
        long playersWithoutMod = totalPlayers - playersWithMod;

        return String.format("Client mod statistics: %d total players, %d with mod (%.1f%%), %d without mod",
                totalPlayers, playersWithMod,
                totalPlayers > 0 ? (playersWithMod * 100.0 / totalPlayers) : 0.0,
                playersWithoutMod);
    }

    public static boolean hasAnyClientsWithMod() {
        return clientModStatus.values().stream().anyMatch(Boolean::booleanValue);
    }

    public static void markPlayerMayNotHaveMod(UUID playerId) {
        if (!clientModStatus.getOrDefault(playerId, false)) {
            clientModStatus.put(playerId, false);
        }
    }
}

package eu.doytchinov.tracecraft.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class ChunkUtils {
    public static int distanceToUnloaded(ServerPlayer player, ServerLevel level) {

        int pcx = player.chunkPosition().x;
        int pcz = player.chunkPosition().z;

        final int MAX_RADIUS = 32;

        for (int d = 0; d <= MAX_RADIUS; d++) {

            //walk the square ring at radius d. We only need to test the
            //outer edge; interior was already proven loaded.
            for (int dx = -d; dx <= d; dx++) {
                int cx1 = pcx + dx;
                int cz1 = pcz +  d;   //north edge
                int cz2 = pcz -  d;   //south edge

                if (!level.hasChunk(cx1, cz1) || !level.hasChunk(cx1, cz2))
                    return d;
            }
            for (int dz = -d + 1; dz <= d - 1; dz++) {
                int cz  = pcz + dz;
                int cx1 = pcx +  d;
                int cx2 = pcx -  d;

                if (!level.hasChunk(cx1, cz) || !level.hasChunk(cx2, cz))
                    return d;
            }
        }
        return MAX_RADIUS;
    }
}


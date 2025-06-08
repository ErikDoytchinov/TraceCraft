package eu.doytchinov.tracecraft.server;

import eu.doytchinov.tracecraft.TraceCraft;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TraceCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.DEDICATED_SERVER)
public final class ServerHooks {

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        WorldInteractionHandler.handleBlockPlace(e);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        WorldInteractionHandler.handleBlockBreak(e);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        PlayerEventHandler.handleLogin(e);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        PlayerEventHandler.handleLogout(e);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        // run exactly once per server tick
        if (event.phase != TickEvent.Phase.END)
            return;
        MinecraftServer server = event.getServer();
        ServerMetricsHandler.handleTickForTPS(event, server);
    }

    @SubscribeEvent
    public static void onTickStart(TickEvent.ServerTickEvent event) {
        ServerMetricsHandler.handleTickStart(event);
    }

    @SubscribeEvent
    public static void onTickEndMetrics(TickEvent.ServerTickEvent event) {
        ServerMetricsHandler.handleTickEndMetrics(event);
    }

    @SubscribeEvent
    public static void onLightUpdate(LevelEvent.Load event) {
        WorldInteractionHandler.handleLightUpdate(event);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        WorldInteractionHandler.handleChunkLoad(event);
    }

    @SubscribeEvent
    public static void onChunkGenerated(ChunkEvent.Load event) {
        WorldInteractionHandler.handleChunkGenerated(event);
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent e) {
        WorldInteractionHandler.handleNeighborNotify(e);
    }

    @SubscribeEvent
    public static void samplePlayerPaths(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        MinecraftServer server = event.getServer();
        PlayerEventHandler.samplePlayerPathsAndProximity(event, server);
    }

    @SubscribeEvent
    public static void onItemUse(PlayerInteractEvent.RightClickItem e) {
        PlayerEventHandler.handleItemUse(e);
    }

    @SubscribeEvent
    public static void onCombatEvent(LivingHurtEvent event) {
        PlayerEventHandler.handleCombatEvent(event);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        PlayerEventHandler.handlePlayerDeath(event);
    }
}

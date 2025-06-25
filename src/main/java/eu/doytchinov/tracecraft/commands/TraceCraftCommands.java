package eu.doytchinov.tracecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import eu.doytchinov.tracecraft.util.ClientModDetection;
import eu.doytchinov.tracecraft.config.ConfigHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class TraceCraftCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("tracecraft")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(TraceCraftCommands::showStatus))
                .then(Commands.literal("players")
                        .executes(TraceCraftCommands::listPlayers))
                .then(Commands.literal("config")
                        .executes(TraceCraftCommands::showConfig)));
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String stats = ClientModDetection.getStatistics();
        source.sendSuccess(() -> Component.literal("TraceCraft Status: " + stats), false);
        return 1;
    }

    private static int listPlayers(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal("TraceCraft Player Status:"), false);

        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            boolean hasMod = ClientModDetection.hasClientMod(player);
            String status = hasMod ? "§aHAS MOD" : "§cNO MOD";
            source.sendSuccess(() -> Component.literal("  " + player.getName().getString() + ": " + status), false);
        }

        return 1;
    }

    private static int showConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal("§6TraceCraft Configuration:"), false);
        source.sendSuccess(() -> Component.literal("§7═══════════════════════════"), false);

        source.sendSuccess(() -> Component.literal("§e[General]"), false);
        source.sendSuccess(() -> Component.literal("  InfluxDB URL: §a" + ConfigHandler.getInfluxdbUrl()), false);

        source.sendSuccess(() -> Component.literal("§e[Metrics]"), false);
        source.sendSuccess(() -> Component.literal("  Dry-run Mode: " +
                (ConfigHandler.isDryRunMode() ? "§eYes" : "§aNo")), false);

        source.sendSuccess(() -> Component.literal("§e[Event Categories]"), false);
        source.sendSuccess(() -> Component.literal("  Block Events: " +
                (ConfigHandler.areBlockEventsEnabled() ? "§aEnabled" : "§cDisabled")), false);
        source.sendSuccess(() -> Component.literal("  Tick Metrics: " +
                (ConfigHandler.areTickMetricsEnabled() ? "§aEnabled" : "§cDisabled")), false);
        source.sendSuccess(() -> Component.literal("  Player Behavior: " +
                (ConfigHandler.isPlayerBehaviorEnabled() ? "§aEnabled" : "§cDisabled")), false);
        source.sendSuccess(() -> Component.literal("  Combat Events: " +
                (ConfigHandler.areCombatEventsEnabled() ? "§aEnabled" : "§cDisabled")), false);
        source.sendSuccess(() -> Component.literal("  World Events: " +
                (ConfigHandler.areWorldEventsEnabled() ? "§aEnabled" : "§cDisabled")), false);
        source.sendSuccess(() -> Component.literal("  Network Metrics: " +
                (ConfigHandler.areNetworkMetricsEnabled() ? "§aEnabled" : "§cDisabled")), false);
        source.sendSuccess(() -> Component.literal("  System Metrics: " +
                (ConfigHandler.areSystemMetricsEnabled() ? "§aEnabled" : "§cDisabled")), false);

        source.sendSuccess(() -> Component.literal("§e[Performance]"), false);
        source.sendSuccess(() -> Component.literal("  Queue Size: §b" + ConfigHandler.getQueueSize()), false);
        source.sendSuccess(() -> Component.literal("  Flush Threshold: §b" + ConfigHandler.getFlushThreshold()), false);

        source.sendSuccess(() -> Component.literal("§e[Sampling Intervals]"), false);
        source.sendSuccess(
                () -> Component
                        .literal("  Player Location: §b" + ConfigHandler.getPlayerLocationSampleInterval() + "ms"),
                false);
        source.sendSuccess(
                () -> Component.literal("  Proximity Check: §b" + ConfigHandler.getProximityCheckInterval() + "ms"),
                false);

        source.sendSuccess(() -> Component.literal("§7═══════════════════════════"), false);
        source.sendSuccess(() -> Component.literal("§7Edit config/tracecraft-common.toml to change settings"), false);

        return 1;
    }
}

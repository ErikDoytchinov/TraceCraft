package eu.doytchinov.tracecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import eu.doytchinov.tracecraft.util.ClientModDetection;
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
                        .executes(TraceCraftCommands::listPlayers)));
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
}

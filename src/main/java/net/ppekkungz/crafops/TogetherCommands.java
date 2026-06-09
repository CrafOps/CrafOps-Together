package net.ppekkungz.crafops;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.ppekkungz.crafops.interaction.InteractionManager;

import static net.minecraft.server.command.CommandManager.literal;

/**
 * TogetherCommands — Registers all /together subcommands.
 *
 * Commands:
 *   /together disconnect  — Disconnect the executing player's current connection
 *   /together debug       — Print all active connections to the executor (op only)
 *
 * Architecture note:
 *   Uses Fabric's CommandRegistrationCallback (v2) which fires during
 *   server startup after the command dispatcher is ready.
 *   No Mixin needed.
 */
public class TogetherCommands {

    /**
     * Register all /together commands.
     * Called from CrafOpsUtils.onInitialize().
     */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("together")
                            // /together disconnect
                            .then(literal("disconnect")
                                    .executes(TogetherCommands::executeDisconnect)
                            )
                            // /together debug  (requires op level 2)
                            .then(literal("debug")
                                    .requires(source -> source.hasPermissionLevel(2))
                                    .executes(TogetherCommands::executeDebug)
                            )
            );
        });
    }

    // ── Command Handlers ──────────────────────────────────────────────────────

    /**
     * /together disconnect
     *
     * Disconnects the executing player from their current hand-hold connection.
     * If the player has no active connection, informs them accordingly.
     *
     * Available to all players.
     */
    private static int executeDisconnect(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        // Command must be run by a player (not console)
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }

        InteractionManager manager = InteractionManager.getInstance();

        if (!manager.isConnected(player.getUuid())) {
            source.sendFeedback(
                    () -> Text.literal("§eYou are not currently holding anyone's hand."),
                    false
            );
            return 0;
        }

        manager.disconnect(player, true);

        source.sendFeedback(
                () -> Text.literal("§aDisconnected."),
                false
        );
        return 1;
    }

    /**
     * /together debug
     *
     * Prints all active hand-hold connections to the executing player.
     * Requires operator permission level 2.
     *
     * Output format:
     *   Active connections: N
     *     HandHoldComponent{uuid → uuid, active=true, tick=12345}
     */
    private static int executeDebug(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        InteractionManager manager = InteractionManager.getInstance();

        String info = manager.getDebugInfo();

        // Split by newline and send each line separately for clean chat display
        String[] lines = info.split("\n");
        for (String line : lines) {
            final String finalLine = line;
            source.sendFeedback(
                    () -> Text.literal("§b[Together Debug] §f" + finalLine),
                    false
            );
        }

        return 1;
    }

    // ── Prevent instantiation ─────────────────────────────────────────────────

    private TogetherCommands() {}
}
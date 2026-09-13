package net.ppekkungz.crafops;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.ppekkungz.crafops.config.TogetherConfig;
import net.ppekkungz.crafops.interaction.InteractionManager;
import net.ppekkungz.crafops.network.TogetherPackets;
import static net.minecraft.server.command.CommandManager.*;

public final class TogetherCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(literal("together")
                .then(literal("disconnect").executes(ctx -> disconnect(ctx.getSource())))
                .then(literal("accept").executes(ctx -> respond(ctx.getSource(), true, null))
                        .then(argument("player", EntityArgumentType.player()).executes(ctx -> respond(ctx.getSource(), true, EntityArgumentType.getPlayer(ctx, "player")))))
                .then(literal("decline").executes(ctx -> respond(ctx.getSource(), false, null))
                        .then(argument("player", EntityArgumentType.player()).executes(ctx -> respond(ctx.getSource(), false, EntityArgumentType.getPlayer(ctx, "player")))))
                .then(literal("debug").requires(source -> source.hasPermissionLevel(2)).executes(TogetherCommands::debug))
                .then(literal("reload").requires(source -> source.hasPermissionLevel(2)).executes(TogetherCommands::reload))));
    }

    private static int disconnect(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean changed = InteractionManager.getInstance().disconnectPlayer(source.getPlayerOrThrow());
        source.sendFeedback(() -> Text.translatable(changed ? "command.crafops_together.disconnected" : "command.crafops_together.not_connected"), false);
        return changed ? 1 : 0;
    }
    private static int respond(ServerCommandSource source, boolean accepted, ServerPlayerEntity sender) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean changed = InteractionManager.getInstance().respondLatest(source.getPlayerOrThrow(), accepted, sender == null ? null : sender.getUuid());
        source.sendFeedback(() -> Text.translatable(changed ? (accepted ? "command.crafops_together.accepted" : "command.crafops_together.declined") : "command.crafops_together.no_request"), false);
        return changed ? 1 : 0;
    }
    private static int debug(CommandContext<ServerCommandSource> ctx) {
        for (String line : InteractionManager.getInstance().getDebugInfo().split("\n")) ctx.getSource().sendFeedback(() -> Text.literal(line), false);
        return 1;
    }
    private static int reload(CommandContext<ServerCommandSource> ctx) {
        boolean ok = TogetherConfig.load();
        TogetherPackets.broadcastSnapshot(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable(ok ? "command.crafops_together.reload_ok" : "command.crafops_together.reload_failed"), true);
        return ok ? 1 : 0;
    }
    private TogetherCommands() {}
}

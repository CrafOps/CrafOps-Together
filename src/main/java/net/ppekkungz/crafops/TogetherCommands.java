package net.ppekkungz.crafops;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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

/** {@code /crafops together <command>}; the debug branch (op level 2) lives in {@link TogetherDebug}. */
public final class TogetherCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(literal("crafops").then(together())));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> together() {
        return literal("together")
                .then(literal("disconnect").executes(ctx -> disconnect(ctx.getSource())))
                .then(literal("accept").executes(ctx -> respond(ctx.getSource(), true, null))
                        .then(argument("player", EntityArgumentType.player()).executes(ctx -> respond(ctx.getSource(), true, EntityArgumentType.getPlayer(ctx, "player")))))
                .then(literal("decline").executes(ctx -> respond(ctx.getSource(), false, null))
                        .then(argument("player", EntityArgumentType.player()).executes(ctx -> respond(ctx.getSource(), false, EntityArgumentType.getPlayer(ctx, "player")))))
                .then(literal("reload").requires(source -> source.hasPermissionLevel(2)).executes(TogetherCommands::reload))
                .then(debug());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> debug() {
        return literal("debug").requires(source -> source.hasPermissionLevel(2))
                .executes(ctx -> TogetherDebug.info(ctx.getSource()))
                .then(literal("info").executes(ctx -> TogetherDebug.info(ctx.getSource())))
                .then(literal("link").then(argument("leader", EntityArgumentType.player()).then(argument("follower", EntityArgumentType.player())
                        .executes(ctx -> TogetherDebug.link(ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "leader"), EntityArgumentType.getPlayer(ctx, "follower"))))))
                .then(literal("unlink").then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> TogetherDebug.unlink(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))))
                .then(literal("fly").then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> TogetherDebug.fly(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), null))
                        .then(argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> TogetherDebug.fly(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"),
                                        BoolArgumentType.getBool(ctx, "enabled"))))))
                .then(literal("watch").executes(ctx -> TogetherDebug.watch(ctx.getSource().getPlayerOrThrow())))
                .then(literal("lag").then(argument("millis", IntegerArgumentType.integer(0, 1000))
                        .executes(ctx -> TogetherDebug.lag(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "millis")))))
                .then(literal("course").executes(ctx -> TogetherDebug.course(ctx.getSource().getPlayerOrThrow())));
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
    private static int reload(CommandContext<ServerCommandSource> ctx) {
        boolean ok = TogetherConfig.load();
        TogetherPackets.broadcastSnapshot(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(() -> Text.translatable(ok ? "command.crafops_together.reload_ok" : "command.crafops_together.reload_failed"), true);
        return ok ? 1 : 0;
    }
    private TogetherCommands() {}
}

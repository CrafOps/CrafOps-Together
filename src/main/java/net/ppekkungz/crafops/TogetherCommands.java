package net.ppekkungz.crafops;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.ppekkungz.crafops.config.TogetherConfig;
import net.ppekkungz.crafops.interaction.InteractionManager;
import net.ppekkungz.crafops.network.TogetherPackets;
import static net.minecraft.commands.Commands.*;

/** {@code /crafops together <command>}; the debug branch (op level 2) lives in {@link TogetherDebug}. */
public final class TogetherCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("crafops").then(together()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> together() {
        return literal("together")
                .then(literal("disconnect").executes(ctx -> disconnect(ctx.getSource())))
                .then(literal("accept").executes(ctx -> respond(ctx.getSource(), true, null))
                        .then(argument("player", EntityArgument.player()).executes(ctx -> respond(ctx.getSource(), true, EntityArgument.getPlayer(ctx, "player")))))
                .then(literal("decline").executes(ctx -> respond(ctx.getSource(), false, null))
                        .then(argument("player", EntityArgument.player()).executes(ctx -> respond(ctx.getSource(), false, EntityArgument.getPlayer(ctx, "player")))))
                .then(literal("reload").requires(source -> source.hasPermission(2)).executes(TogetherCommands::reload))
                .then(debug());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debug() {
        return literal("debug").requires(source -> source.hasPermission(2))
                .executes(ctx -> TogetherDebug.info(ctx.getSource()))
                .then(literal("info").executes(ctx -> TogetherDebug.info(ctx.getSource())))
                .then(literal("link").then(argument("leader", EntityArgument.player()).then(argument("follower", EntityArgument.player())
                        .executes(ctx -> TogetherDebug.link(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "leader"), EntityArgument.getPlayer(ctx, "follower"))))))
                .then(literal("unlink").then(argument("player", EntityArgument.player())
                        .executes(ctx -> TogetherDebug.unlink(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(literal("fly").then(argument("player", EntityArgument.player())
                        .executes(ctx -> TogetherDebug.fly(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), null))
                        .then(argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> TogetherDebug.fly(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                        BoolArgumentType.getBool(ctx, "enabled"))))))
                .then(literal("watch").executes(ctx -> TogetherDebug.watch(ctx.getSource().getPlayerOrException())))
                .then(literal("drive").then(argument("player", EntityArgument.player())
                        .then(argument("speed", DoubleArgumentType.doubleArg(0, 1))
                                .executes(ctx -> TogetherDebug.drive(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                        DoubleArgumentType.getDouble(ctx, "speed"), null))
                                .then(argument("yaw", FloatArgumentType.floatArg(-180, 180))
                                        .executes(ctx -> TogetherDebug.drive(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                DoubleArgumentType.getDouble(ctx, "speed"), FloatArgumentType.getFloat(ctx, "yaw")))))))
                .then(literal("lag").then(argument("millis", IntegerArgumentType.integer(0, 1000))
                        .executes(ctx -> TogetherDebug.lag(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "millis")))))
                .then(literal("course").executes(ctx -> TogetherDebug.course(ctx.getSource().getPlayerOrException())));
    }

    private static int disconnect(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean changed = InteractionManager.getInstance().disconnectPlayer(source.getPlayerOrException());
        source.sendSuccess(() -> Component.translatable(changed ? "command.crafops_together.disconnected" : "command.crafops_together.not_connected"), false);
        return changed ? 1 : 0;
    }
    private static int respond(CommandSourceStack source, boolean accepted, ServerPlayer sender) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        boolean changed = InteractionManager.getInstance().respondLatest(source.getPlayerOrException(), accepted, sender == null ? null : sender.getUUID());
        source.sendSuccess(() -> Component.translatable(changed ? (accepted ? "command.crafops_together.accepted" : "command.crafops_together.declined") : "command.crafops_together.no_request"), false);
        return changed ? 1 : 0;
    }
    private static int reload(CommandContext<CommandSourceStack> ctx) {
        boolean ok = TogetherConfig.load();
        TogetherPackets.broadcastSnapshot(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "command.crafops_together.reload_ok" : "command.crafops_together.reload_failed"), true);
        return ok ? 1 : 0;
    }
    private TogetherCommands() {}
}

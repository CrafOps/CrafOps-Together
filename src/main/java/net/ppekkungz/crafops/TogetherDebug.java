package net.ppekkungz.crafops;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.ppekkungz.crafops.interaction.HandHoldComponent;
import net.ppekkungz.crafops.interaction.InteractionManager;
import net.ppekkungz.crafops.network.TogetherPackets;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Op-only test tools under {@code /crafops together debug}. */
public final class TogetherDebug {
    private static final Set<UUID> watchers = new HashSet<>();

    public static int info(ServerCommandSource source) {
        for (String line : InteractionManager.getInstance().getDebugInfo(source.getServer()).split("\n")) source.sendFeedback(() -> Text.literal(line), false);
        return 1;
    }

    /** Links two players without a request, placing the follower on the leader's right first. */
    public static int link(ServerCommandSource source, ServerPlayerEntity leader, ServerPlayerEntity follower) {
        String error = InteractionManager.getInstance().debugConnect(leader, follower);
        if (error != null) { source.sendError(Text.translatable(error)); return 0; }
        source.sendFeedback(() -> Text.literal("Linked " + name(leader) + " -> " + name(follower)), true);
        return 1;
    }

    public static int unlink(ServerCommandSource source, ServerPlayerEntity player) {
        boolean changed = InteractionManager.getInstance().disconnectPlayer(player);
        source.sendFeedback(() -> Text.literal(changed ? "Unlinked " + name(player) : name(player) + " is not linked"), true);
        return changed ? 1 : 0;
    }

    /** Sets or toggles ability flight, e.g. for a scripted fake player that cannot double-jump. */
    public static int fly(ServerCommandSource source, ServerPlayerEntity player, Boolean enabled) {
        var abilities = player.getAbilities();
        abilities.allowFlying = true;
        abilities.flying = enabled == null ? !abilities.flying : enabled;
        player.sendAbilitiesUpdate();
        boolean flying = abilities.flying;
        source.sendFeedback(() -> Text.literal(name(player) + " flying: " + flying), true);
        return 1;
    }

    /** Toggles a live action-bar readout of the caller's formation, measured in the leader's frame. */
    public static int watch(ServerPlayerEntity player) {
        boolean on = watchers.add(player.getUuid()) || !watchers.remove(player.getUuid());
        player.sendMessage(Text.literal("Formation watch " + (on ? "on" : "off")), false);
        return 1;
    }

    /** Holds back partner-state relays to imitate a high-ping connection. */
    public static int lag(ServerCommandSource source, int millis) {
        TogetherPackets.setSimulatedLag(millis);
        source.sendFeedback(() -> Text.literal(millis == 0 ? "Simulated relay lag off" : "Partner relays now delayed by " + millis + " ms"), true);
        return 1;
    }

    public static void tick(MinecraftServer server) {
        if (watchers.isEmpty()) return;
        watchers.removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
        for (UUID id : watchers) {
            ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(id);
            HandHoldComponent link = InteractionManager.getInstance().findLink(id);
            if (link == null) { viewer.sendMessage(Text.literal("not holding hands").formatted(Formatting.GRAY), true); continue; }
            ServerPlayerEntity leader = server.getPlayerManager().getPlayer(link.getLeaderUuid());
            ServerPlayerEntity follower = server.getPlayerManager().getPlayer(link.getFollowerUuid());
            if (leader == null || follower == null) continue;
            double yaw = Math.toRadians(leader.bodyYaw);
            double dx = follower.getX() - leader.getX(), dz = follower.getZ() - leader.getZ();
            double side = dx * -Math.cos(yaw) + dz * -Math.sin(yaw);
            double behind = -(dx * -Math.sin(yaw) + dz * Math.cos(yaw));
            double dy = follower.getY() - leader.getY();
            double speed = Math.hypot(leader.getVelocity().x, leader.getVelocity().z) * 20;
            viewer.sendMessage(Text.literal(String.format(Locale.ROOT, "side %.2f  behind %.2f  dy %.2f  |  %s%s  |  ping %d/%d ms%s",
                    side, behind, dy, leader.getAbilities().flying ? "flying " : "", leader.isSprinting() ? "sprint" : speed > 0.5 ? "walk" : "idle",
                    leader.pingMilliseconds, follower.pingMilliseconds,
                    TogetherPackets.simulatedLag() > 0 ? " +" + TogetherPackets.simulatedLag() + " lag" : ""))
                    .formatted(Formatting.AQUA), true);
        }
    }

    /**
     * Builds a test course ahead of the caller: flat path, stairs, hurdles, a pool, then a run-out.
     * Coordinates below are (across, up, along) relative to the block the caller stands on.
     */
    public static int course(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Direction forward = player.getHorizontalFacing();
        Course c = new Course(world, player.getBlockPos().down(), forward);
        c.fill(-5, 1, 1, 5, 6, 92, Blocks.AIR.getDefaultState());
        c.fill(-4, 0, 1, 4, 0, 92, Blocks.SMOOTH_STONE.getDefaultState());
        c.fill(0, 0, 1, 0, 0, 92, Blocks.GOLD_BLOCK.getDefaultState());
        c.fill(-5, 0, 1, -5, 0, 92, Blocks.POLISHED_ANDESITE.getDefaultState());
        c.fill(5, 0, 1, 5, 0, 92, Blocks.POLISHED_ANDESITE.getDefaultState());
        for (int i = 0; i < 4; i++) c.fill(-4, 1, 16 + i * 2, 4, 1 + i, 17 + i * 2, Blocks.STONE_BRICKS.getDefaultState());
        c.fill(-4, 1, 24, 4, 4, 25, Blocks.STONE_BRICKS.getDefaultState());
        for (int i = 0; i < 3; i++) c.fill(-4, 1, 26 + i * 2, 4, 3 - i, 27 + i * 2, Blocks.STONE_BRICKS.getDefaultState());
        for (int z = 36; z <= 48; z += 4) c.fill(-4, 1, z, 4, 1, z, Blocks.OAK_PLANKS.getDefaultState());
        c.fill(-4, -3, 54, 4, 0, 72, Blocks.WATER.getDefaultState());
        c.fill(-4, -4, 54, 4, -4, 72, Blocks.SAND.getDefaultState());
        for (int z = 1; z <= 92; z += 10) { c.fill(-5, 1, z, -5, 1, z, Blocks.LANTERN.getDefaultState()); c.fill(5, 1, z, 5, 1, z, Blocks.LANTERN.getDefaultState()); }
        player.sendMessage(Text.literal("Course built ahead of you: flat, stairs, hurdles, pool, run-out (92 blocks)."), false);
        return 1;
    }

    private record Course(ServerWorld world, BlockPos origin, Direction forward) {
        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
            Direction right = forward.rotateYClockwise();
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                        world.setBlockState(origin.offset(right, x).offset(forward, z).up(y), state);
        }
    }

    private static String name(ServerPlayerEntity player) { return player.getGameProfile().getName(); }
    private TogetherDebug() {}
}

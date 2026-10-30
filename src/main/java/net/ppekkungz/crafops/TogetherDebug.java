package net.ppekkungz.crafops;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.ppekkungz.crafops.interaction.HandHoldComponent;
import net.ppekkungz.crafops.interaction.InteractionManager;
import net.ppekkungz.crafops.network.TogetherPackets;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Op-only test tools under {@code /crafops together debug}. */
public final class TogetherDebug {
    private static final Set<UUID> watchers = new HashSet<>();
    private static final Map<UUID, Drive> drivers = new HashMap<>();

    public static int info(CommandSourceStack source) {
        for (String line : InteractionManager.getInstance().getDebugInfo(source.getServer()).split("\n")) source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    /** Links two players without a request, placing the follower on the leader's right first. */
    public static int link(CommandSourceStack source, ServerPlayer leader, ServerPlayer follower) {
        String error = InteractionManager.getInstance().debugConnect(leader, follower);
        if (error != null) { source.sendFailure(Component.translatable(error)); return 0; }
        source.sendSuccess(() -> Component.literal("Linked " + name(leader) + " -> " + name(follower)), true);
        return 1;
    }

    public static int unlink(CommandSourceStack source, ServerPlayer player) {
        boolean changed = InteractionManager.getInstance().disconnectPlayer(player);
        source.sendSuccess(() -> Component.literal(changed ? "Unlinked " + name(player) : name(player) + " is not linked"), true);
        return changed ? 1 : 0;
    }

    /** Sets or toggles ability flight, e.g. for a scripted fake player that cannot double-jump. */
    public static int fly(CommandSourceStack source, ServerPlayer player, Boolean enabled) {
        var abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = enabled == null ? !abilities.flying : enabled;
        player.onUpdateAbilities();
        boolean flying = abilities.flying;
        source.sendSuccess(() -> Component.literal(name(player) + " flying: " + flying), true);
        return 1;
    }

    /** Toggles a live action-bar readout of the caller's formation, measured in the leader's frame. */
    public static int watch(ServerPlayer player) {
        boolean on = watchers.add(player.getUUID()) || !watchers.remove(player.getUUID());
        player.displayClientMessage(Component.literal("Formation watch " + (on ? "on" : "off")), false);
        return 1;
    }

    /** Holds back partner-state relays to imitate a high-ping connection. */
    public static int lag(CommandSourceStack source, int millis) {
        TogetherPackets.setSimulatedLag(millis);
        source.sendSuccess(() -> Component.literal(millis == 0 ? "Simulated relay lag off" : "Partner relays now delayed by " + millis + " ms"), true);
        return 1;
    }

    /**
     * Test stand-in for a scripted leader (no Carpet on NeoForge): pushes the player's velocity every tick so
     * their own client walks with real physics and reports real speed. Speed is blocks/tick; 0 stops.
     */
    public static int drive(CommandSourceStack source, ServerPlayer player, double speed, Float yaw) {
        if (speed <= 0) { drivers.remove(player.getUUID()); source.sendSuccess(() -> Component.literal("Stopped driving " + name(player)), true); return 1; }
        float heading = yaw == null ? player.getYRot() : yaw;
        player.setYRot(heading);
        player.setYHeadRot(heading);
        drivers.put(player.getUUID(), new Drive(-Math.sin(Math.toRadians(heading)) * speed, Math.cos(Math.toRadians(heading)) * speed));
        source.sendSuccess(() -> Component.literal("Driving " + name(player) + " at " + speed + " blocks/tick, yaw " + heading), true);
        return 1;
    }

    private record Drive(double velocityX, double velocityZ) {}

    public static void tick(MinecraftServer server) {
        drivers.entrySet().removeIf(entry -> server.getPlayerList().getPlayer(entry.getKey()) == null);
        drivers.forEach((id, drive) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            player.setDeltaMovement(drive.velocityX(), player.getDeltaMovement().y, drive.velocityZ());
            player.hurtMarked = true;
        });
        if (watchers.isEmpty()) return;
        watchers.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (UUID id : watchers) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(id);
            HandHoldComponent link = InteractionManager.getInstance().findLink(id);
            if (link == null) { viewer.displayClientMessage(Component.literal("not holding hands").withStyle(ChatFormatting.GRAY), true); continue; }
            ServerPlayer leader = server.getPlayerList().getPlayer(link.getLeaderUuid());
            ServerPlayer follower = server.getPlayerList().getPlayer(link.getFollowerUuid());
            if (leader == null || follower == null) continue;
            double yaw = Math.toRadians(leader.yBodyRot);
            double dx = follower.getX() - leader.getX(), dz = follower.getZ() - leader.getZ();
            double side = dx * -Math.cos(yaw) + dz * -Math.sin(yaw);
            double behind = -(dx * -Math.sin(yaw) + dz * Math.cos(yaw));
            double dy = follower.getY() - leader.getY();
            double speed = Math.hypot(leader.getDeltaMovement().x, leader.getDeltaMovement().z) * 20;
            viewer.displayClientMessage(Component.literal(String.format(Locale.ROOT, "side %.2f  behind %.2f  dy %.2f  |  %s%s  |  ping %d/%d ms%s",
                    side, behind, dy, leader.getAbilities().flying ? "flying " : "", leader.isSprinting() ? "sprint" : speed > 0.5 ? "walk" : "idle",
                    leader.connection.latency(), follower.connection.latency(),
                    TogetherPackets.simulatedLag() > 0 ? " +" + TogetherPackets.simulatedLag() + " lag" : ""))
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /**
     * Builds a test course ahead of the caller: flat path, stairs, hurdles, a pool, then a run-out.
     * Coordinates below are (across, up, along) relative to the block the caller stands on.
     */
    public static int course(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        Direction forward = player.getDirection();
        Course c = new Course(world, player.blockPosition().below(), forward);
        c.fill(-5, 1, 1, 5, 6, 92, Blocks.AIR.defaultBlockState());
        c.fill(-4, 0, 1, 4, 0, 92, Blocks.SMOOTH_STONE.defaultBlockState());
        c.fill(0, 0, 1, 0, 0, 92, Blocks.GOLD_BLOCK.defaultBlockState());
        c.fill(-5, 0, 1, -5, 0, 92, Blocks.POLISHED_ANDESITE.defaultBlockState());
        c.fill(5, 0, 1, 5, 0, 92, Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int i = 0; i < 4; i++) c.fill(-4, 1, 16 + i * 2, 4, 1 + i, 17 + i * 2, Blocks.STONE_BRICKS.defaultBlockState());
        c.fill(-4, 1, 24, 4, 4, 25, Blocks.STONE_BRICKS.defaultBlockState());
        for (int i = 0; i < 3; i++) c.fill(-4, 1, 26 + i * 2, 4, 3 - i, 27 + i * 2, Blocks.STONE_BRICKS.defaultBlockState());
        for (int z = 36; z <= 48; z += 4) c.fill(-4, 1, z, 4, 1, z, Blocks.OAK_PLANKS.defaultBlockState());
        c.fill(-4, -3, 54, 4, 0, 72, Blocks.WATER.defaultBlockState());
        c.fill(-4, -4, 54, 4, -4, 72, Blocks.SAND.defaultBlockState());
        for (int z = 1; z <= 92; z += 10) { c.fill(-5, 1, z, -5, 1, z, Blocks.LANTERN.defaultBlockState()); c.fill(5, 1, z, 5, 1, z, Blocks.LANTERN.defaultBlockState()); }
        player.displayClientMessage(Component.literal("Course built ahead of you: flat, stairs, hurdles, pool, run-out (92 blocks)."), false);
        return 1;
    }

    private record Course(ServerLevel world, BlockPos origin, Direction forward) {
        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
            Direction right = forward.getClockWise();
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                        world.setBlockAndUpdate(origin.relative(right, x).relative(forward, z).above(y), state);
        }
    }

    private static String name(ServerPlayer player) { return player.getGameProfile().getName(); }
    private TogetherDebug() {}
}

package net.ppekkungz.crafops.client.renderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.ppekkungz.crafops.client.animation.ArmAnimationState;
import net.minecraft.util.math.Vec3d;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Canonical render-space formation, resolved from the chain head so every observer sees the same line. */
public final class HandHoldVisuals {

    public static Vec3d interpolatedPosition(AbstractClientPlayerEntity player, float delta) {
        return new Vec3d(lerp(player.prevX, player.getX(), delta), lerp(player.prevY, player.getY(), delta),
                lerp(player.prevZ, player.getZ(), delta));
    }

    /** Where {@code follower} should be drawn this frame; its own Y is kept so steps and swimming stay honest. */
    public static Vec3d expectedFollowerPosition(AbstractClientPlayerEntity follower, float delta) {
        return canonicalPosition(follower, delta, new HashSet<>());
    }

    /**
     * Offset the renderer applies so a holding pair is always drawn hand in hand, on every screen:
     * <ul>
     *   <li>On the follower's own screen the remote leader is drawn at its formation spot beside the follower.
     *       Remote players arrive a few ticks late, so without this the follower would look like the one pulling.</li>
     *   <li>Everywhere else a remote follower is drawn at its formation spot beside its (canonical) leader.</li>
     * </ul>
     * Height is locked too while the pair swims or flies. The lock stays at full strength and lets go only past
     * {@link #RELEASE_DISTANCE} (a teleport), so the hands never drift apart and snap back.
     */
    public static Vec3d renderCorrection(AbstractClientPlayerEntity player, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player == client.player || player.getWorld() == null) return Vec3d.ZERO;
        Vec3d actual = interpolatedPosition(player, delta);

        ClientConnectionCache.Link localIncoming = client.player == null ? null : ClientConnectionCache.incomingLink(client.player.getUuid());
        if (localIncoming != null && localIncoming.leader().equals(player.getUuid())) {
            Vec3d follower = interpolatedPosition(client.player, delta);
            Vec3d offset = formationOffset(localIncoming, player, delta);
            Vec3d target = new Vec3d(follower.x - offset.x, lockHeight(player, client.player) ? follower.y : basePosition(player, delta).y, follower.z - offset.z);
            return weighted(target.subtract(actual), ArmAnimationState.right(player.getUuid(), delta));
        }
        if (ClientConnectionCache.hasIncoming(player.getUuid()))
            return weighted(expectedFollowerPosition(player, delta).subtract(actual), ArmAnimationState.left(player.getUuid(), delta));
        // A chain's head is drawn at its relayed, ping-compensated position, so observers see it without the tracker lag.
        if (ClientConnectionCache.hasOutgoing(player.getUuid()))
            return weighted(basePosition(player, delta).subtract(actual), ArmAnimationState.right(player.getUuid(), delta));
        return Vec3d.ZERO;
    }

    /** A remote holding player's best-known current position: relayed and ping-compensated, else vanilla's. */
    private static Vec3d basePosition(AbstractClientPlayerEntity player, float delta) {
        if (player != MinecraftClient.getInstance().player) {
            Vec3d relayed = ClientConnectionCache.displayPosition(player.getUuid());
            if (relayed != null) return relayed;
        }
        return interpolatedPosition(player, delta);
    }

    private static Vec3d weighted(Vec3d correction, float progress) {
        if (correction.lengthSquared() > RELEASE_DISTANCE * RELEASE_DISTANCE || progress <= 0f) return Vec3d.ZERO;
        return correction.multiply(progress);
    }

    /** Where the follower stands relative to its leader: on the right, a touch behind while walking. */
    private static Vec3d formationOffset(ClientConnectionCache.Link link, AbstractClientPlayerEntity leader, float delta) {
        double yaw = Math.toRadians(ClientConnectionCache.formationYaw(link, delta, leader.bodyYaw));
        double side = ClientConnectionCache.settings().sideOffset();
        double behind = ClientConnectionCache.behindOffset(link, delta);
        return new Vec3d(-Math.cos(yaw) * side + Math.sin(yaw) * behind, 0, -Math.sin(yaw) * side - Math.cos(yaw) * behind);
    }

    /**
     * On the ground each partner keeps their own height (steps, jumps); in water or the air the pair moves as one.
     * Either partner in water is enough, so stepping in or climbing out never pulls the hands apart.
     */
    private static boolean lockHeight(PlayerEntity a, PlayerEntity b) {
        return a.isTouchingWater() || b.isTouchingWater() || isFlying(a) || isFlying(b);
    }

    private static boolean isFlying(PlayerEntity player) {
        return player == MinecraftClient.getInstance().player ? player.getAbilities().flying : ClientConnectionCache.isFlying(player.getUuid());
    }

    /** Where a player is actually drawn this frame, including any lag correction. */
    public static Vec3d renderedPosition(AbstractClientPlayerEntity player, float delta) {
        return interpolatedPosition(player, delta).add(renderCorrection(player, delta));
    }

    /** Beyond this gap the pair is genuinely apart (e.g. mid-teleport), so each is drawn where it really is. */
    private static final double RELEASE_DISTANCE = 6.0;

    private static Vec3d canonicalPosition(AbstractClientPlayerEntity player, float delta, Set<UUID> visited) {
        Vec3d own = basePosition(player, delta);
        if (!visited.add(player.getUuid())) return own;
        ClientConnectionCache.Link incoming = ClientConnectionCache.incomingLink(player.getUuid());
        if (incoming == null || player.getWorld() == null) return own;
        if (!(player.getWorld().getPlayerByUuid(incoming.leader()) instanceof AbstractClientPlayerEntity leader)) return own;
        Vec3d leaderPosition = canonicalPosition(leader, delta, visited);
        Vec3d offset = formationOffset(incoming, leader, delta);
        return new Vec3d(leaderPosition.x + offset.x, lockHeight(leader, player) ? leaderPosition.y : own.y, leaderPosition.z + offset.z);
    }

    private static double lerp(double a, double b, float t) { return a + (b - a) * t; }
    private HandHoldVisuals() {}
}

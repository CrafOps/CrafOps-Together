package net.ppekkungz.crafops.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.ppekkungz.crafops.client.animation.ArmAnimationState;
import net.minecraft.world.phys.Vec3;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Canonical render-space formation, resolved from the chain head so every observer sees the same line. */
public final class HandHoldVisuals {

    public static Vec3 interpolatedPosition(AbstractClientPlayer player, float delta) {
        return new Vec3(lerp(player.xo, player.getX(), delta), lerp(player.yo, player.getY(), delta),
                lerp(player.zo, player.getZ(), delta));
    }

    /** Where {@code follower} should be drawn this frame; its own Y is kept so steps and swimming stay honest. */
    public static Vec3 expectedFollowerPosition(AbstractClientPlayer follower, float delta) {
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
    public static Vec3 renderCorrection(AbstractClientPlayer player, float delta) {
        Minecraft client = Minecraft.getInstance();
        if (player == client.player || player.level() == null) return Vec3.ZERO;
        Vec3 actual = interpolatedPosition(player, delta);

        ClientConnectionCache.Link localIncoming = client.player == null ? null : ClientConnectionCache.incomingLink(client.player.getUUID());
        if (localIncoming != null && localIncoming.leader().equals(player.getUUID())) {
            Vec3 follower = interpolatedPosition(client.player, delta);
            Vec3 offset = formationOffset(localIncoming, player, delta);
            Vec3 target = new Vec3(follower.x - offset.x, lockHeight(player, client.player) ? follower.y : basePosition(player, delta).y, follower.z - offset.z);
            return weighted(target.subtract(actual), ArmAnimationState.right(player.getUUID(), delta));
        }
        if (ClientConnectionCache.hasIncoming(player.getUUID()))
            return weighted(expectedFollowerPosition(player, delta).subtract(actual), ArmAnimationState.left(player.getUUID(), delta));
        // A chain's head is drawn at its relayed, ping-compensated position, so observers see it without the tracker lag.
        if (ClientConnectionCache.hasOutgoing(player.getUUID()))
            return weighted(basePosition(player, delta).subtract(actual), ArmAnimationState.right(player.getUUID(), delta));
        return Vec3.ZERO;
    }

    /** A remote holding player's best-known current position: relayed and ping-compensated, else vanilla's. */
    private static Vec3 basePosition(AbstractClientPlayer player, float delta) {
        if (player != Minecraft.getInstance().player) {
            Vec3 relayed = ClientConnectionCache.displayPosition(player.getUUID());
            if (relayed != null) return relayed;
        }
        return interpolatedPosition(player, delta);
    }

    private static Vec3 weighted(Vec3 correction, float progress) {
        if (correction.lengthSqr() > RELEASE_DISTANCE * RELEASE_DISTANCE || progress <= 0f) return Vec3.ZERO;
        return correction.scale(progress);
    }

    /** Where the follower stands relative to its leader: on the right, a touch behind while walking. */
    private static Vec3 formationOffset(ClientConnectionCache.Link link, AbstractClientPlayer leader, float delta) {
        double yaw = Math.toRadians(ClientConnectionCache.formationYaw(link, delta, leader.yBodyRot));
        double side = ClientConnectionCache.settings().sideOffset();
        double behind = ClientConnectionCache.behindOffset(link, delta);
        return new Vec3(-Math.cos(yaw) * side + Math.sin(yaw) * behind, 0, -Math.sin(yaw) * side - Math.cos(yaw) * behind);
    }

    /**
     * On the ground each partner keeps their own height (steps, jumps); in water or flying together the pair moves
     * as one. Either partner in water is enough, so stepping in or climbing out never pulls the hands apart. Flight
     * needs both: a walker beside a flyer stays on the ground (the arms reach up instead) rather than being drawn afloat.
     */
    private static boolean lockHeight(Player a, Player b) {
        return a.isInWater() || b.isInWater() || isFlying(a) && isFlying(b);
    }

    private static boolean isFlying(Player player) {
        return player == Minecraft.getInstance().player ? player.getAbilities().flying : ClientConnectionCache.isFlying(player.getUUID());
    }

    /** Where a player is actually drawn this frame, including any lag correction. */
    public static Vec3 renderedPosition(AbstractClientPlayer player, float delta) {
        return interpolatedPosition(player, delta).add(renderCorrection(player, delta));
    }

    /** Beyond this gap the pair is genuinely apart (e.g. mid-teleport), so each is drawn where it really is. */
    private static final double RELEASE_DISTANCE = 6.0;

    private static Vec3 canonicalPosition(AbstractClientPlayer player, float delta, Set<UUID> visited) {
        Vec3 own = basePosition(player, delta);
        if (!visited.add(player.getUUID())) return own;
        ClientConnectionCache.Link incoming = ClientConnectionCache.incomingLink(player.getUUID());
        if (incoming == null || player.level() == null) return own;
        if (!(player.level().getPlayerByUUID(incoming.leader()) instanceof AbstractClientPlayer leader)) return own;
        Vec3 leaderPosition = canonicalPosition(leader, delta, visited);
        Vec3 offset = formationOffset(incoming, leader, delta);
        return new Vec3(leaderPosition.x + offset.x, lockHeight(leader, player) ? leaderPosition.y : own.y, leaderPosition.z + offset.z);
    }

    private static double lerp(double a, double b, float t) { return a + (b - a) * t; }
    private HandHoldVisuals() {}
}

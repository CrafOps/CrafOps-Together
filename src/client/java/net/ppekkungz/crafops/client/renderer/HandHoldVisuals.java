package net.ppekkungz.crafops.client.renderer;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;

import java.util.HashSet;
import java.util.Set;

/** Canonical render-space formation used identically by every client. */
public final class HandHoldVisuals {
    public static final double HAND_HEIGHT = 0.80;

    public static Vec3d interpolatedPosition(AbstractClientPlayerEntity player, float delta) {
        return new Vec3d(lerp(player.prevX, player.getX(), delta), lerp(player.prevY, player.getY(), delta),
                lerp(player.prevZ, player.getZ(), delta));
    }

    public static Vec3d expectedFollowerPosition(AbstractClientPlayerEntity leader, float delta,
                                                  ClientConnectionCache.Link link) {
        Vec3d position = canonicalPosition(leader, delta, new HashSet<>());
        Basis basis = basis(lerpAngle(leader.prevBodyYaw, leader.bodyYaw, delta));
        double behind = ClientConnectionCache.effectiveBehindOffset(link);
        return position.add(
                basis.rightX * ClientConnectionCache.formationSideOffset()
                        - basis.forwardX * behind,
                0,
                basis.rightZ * ClientConnectionCache.formationSideOffset()
                        - basis.forwardZ * behind);
    }

    public static Vec3d leaderHand(AbstractClientPlayerEntity leader, float delta, ClientConnectionCache.Link link) {
        return contactPoint(leader, delta, link, -ClientConnectionCache.visualHandGap() * .5);
    }

    public static Vec3d followerHand(AbstractClientPlayerEntity leader, float delta, ClientConnectionCache.Link link) {
        return contactPoint(leader, delta, link, ClientConnectionCache.visualHandGap() * .5);
    }

    private static Vec3d contactPoint(AbstractClientPlayerEntity leader, float delta, ClientConnectionCache.Link link,
                                      double gapOffset) {
        Vec3d position = canonicalPosition(leader, delta, new HashSet<>());
        Basis basis = basis(lerpAngle(leader.prevBodyYaw, leader.bodyYaw, delta));
        double side = ClientConnectionCache.formationSideOffset() * .5 + gapOffset;
        double behind = ClientConnectionCache.effectiveBehindOffset(link) * .5;
        return position.add(basis.rightX * side - basis.forwardX * behind, HAND_HEIGHT,
                basis.rightZ * side - basis.forwardZ * behind);
    }

    /** Resolves chains from their head so every observer renders the same continuous formation. */
    private static Vec3d canonicalPosition(AbstractClientPlayerEntity player, float delta, Set<java.util.UUID> visited) {
        if (!visited.add(player.getUuid())) return interpolatedPosition(player, delta);
        ClientConnectionCache.Link incoming = ClientConnectionCache.incomingLink(player.getUuid());
        if (incoming == null || player.getWorld() == null) return interpolatedPosition(player, delta);
        var rawLeader = player.getWorld().getPlayerByUuid(incoming.leader());
        if (!(rawLeader instanceof AbstractClientPlayerEntity leader)) return interpolatedPosition(player, delta);
        Vec3d leaderPosition = canonicalPosition(leader, delta, visited);
        Basis basis = basis(lerpAngle(leader.prevBodyYaw, leader.bodyYaw, delta));
        double behind = ClientConnectionCache.effectiveBehindOffset(incoming);
        return leaderPosition.add(
                basis.rightX * ClientConnectionCache.formationSideOffset() - basis.forwardX * behind,
                0,
                basis.rightZ * ClientConnectionCache.formationSideOffset() - basis.forwardZ * behind);
    }

    public static float interpolatedBodyYaw(AbstractClientPlayerEntity player, float delta) {
        return lerpAngle(player.prevBodyYaw, player.bodyYaw, delta);
    }
    private static Basis basis(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Basis(-Math.sin(yaw), Math.cos(yaw), -Math.cos(yaw), -Math.sin(yaw));
    }
    private static double lerp(double a, double b, float t) { return a + (b - a) * t; }
    private static float lerpAngle(float a, float b, float t) {
        float difference = (b - a) % 360f;
        if (difference > 180f) difference -= 360f;
        if (difference < -180f) difference += 360f;
        return a + difference * t;
    }
    private record Basis(double forwardX, double forwardZ, double rightX, double rightZ) {}
    private HandHoldVisuals() {}
}

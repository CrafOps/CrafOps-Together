package net.ppekkungz.crafops.client.cache;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

import java.util.*;

/** Complete client mirror, replaced atomically whenever the server sends a snapshot. */
public final class ClientConnectionCache {
    public record Link(UUID id, UUID leader, UUID follower) {}
    private static Map<UUID, Link> links = Map.of();
    private static boolean visualsEnabled = true;
    private static double renderDistance = 64.0;
    private static double formationSideOffset = .92;
    private static double formationBehindOffset = 0;
    private static double formationTolerance = .03;
    private static double formationSpringStrength = .48;
    private static double formationAcceleration = .24;
    private static double formationMaxCorrectionSpeed = 1.0;
    private static double leaderVelocityMatch = .95;
    private static double stepAssistVelocity = .18;
    private static double visualHandGap = .08;
    private static double maxVisualPositionCorrection = 2.0;
    private static double movingFormationBehindOffset = .28;
    private static double movementDetectionSpeed = .025;
    private static double movementBlendSpeed = .14;
    private static double maxJumpArmLift = .55;
    private static final Map<UUID, MotionState> motionByLink = new HashMap<>();

    public static void replace(Collection<Link> snapshot) {
        Map<UUID, Link> next = new LinkedHashMap<>();
        for (Link link : snapshot) next.put(link.id(), link);
        links = Collections.unmodifiableMap(next);
        motionByLink.keySet().retainAll(next.keySet());
        next.keySet().forEach(id -> motionByLink.computeIfAbsent(id, ignored -> new MotionState()));
    }
    public static Collection<Link> getLinks() { return links.values(); }
    public static boolean isConnected(UUID player) { return hasIncoming(player) || hasOutgoing(player); }
    public static boolean isAdjacent(UUID first, UUID second) { return links.values().stream().anyMatch(l ->
            (l.leader().equals(first) && l.follower().equals(second)) || (l.leader().equals(second) && l.follower().equals(first))); }
    public static boolean hasIncoming(UUID player) { return links.values().stream().anyMatch(l -> l.follower().equals(player)); }
    public static boolean hasOutgoing(UUID player) { return links.values().stream().anyMatch(l -> l.leader().equals(player)); }
    public static UUID incomingLeader(UUID player) {
        Link link = incomingLink(player);
        return link == null ? null : link.leader();
    }
    public static UUID outgoingFollower(UUID player) {
        Link link = outgoingLink(player);
        return link == null ? null : link.follower();
    }
    public static Link incomingLink(UUID player) {
        for (Link link : links.values()) if (link.follower().equals(player)) return link;
        return null;
    }
    public static Link outgoingLink(UUID player) {
        for (Link link : links.values()) if (link.leader().equals(player)) return link;
        return null;
    }
    public static void setSettings(boolean enabled, double distance, double side, double behind, double tolerance,
                                   double spring, double acceleration, double maxSpeed, double velocityMatch, double stepAssist,
                                   double handGap, double visualCorrection, double movingBehind, double movementThreshold,
                                   double blendSpeed, double jumpArmLift) {
        visualsEnabled = enabled; renderDistance = distance;
        formationSideOffset = side; formationBehindOffset = behind; formationTolerance = tolerance;
        formationSpringStrength = spring; formationAcceleration = acceleration;
        formationMaxCorrectionSpeed = maxSpeed; leaderVelocityMatch = velocityMatch; stepAssistVelocity = stepAssist;
        visualHandGap = handGap; maxVisualPositionCorrection = visualCorrection;
        movingFormationBehindOffset = movingBehind; movementDetectionSpeed = movementThreshold;
        movementBlendSpeed = blendSpeed; maxJumpArmLift = jumpArmLift;
    }

    /** Advances visual movement/jump state once per client tick using the same movement threshold as the server. */
    public static void tickMotion(ClientWorld world) {
        for (Link link : links.values()) {
            MotionState state = motionByLink.computeIfAbsent(link.id(), ignored -> new MotionState());
            var rawLeader = world.getPlayerByUuid(link.leader());
            var rawFollower = world.getPlayerByUuid(link.follower());
            if (!(rawLeader instanceof AbstractClientPlayerEntity leader)
                    || !(rawFollower instanceof AbstractClientPlayerEntity follower)) continue;

            double movedX = leader.getX() - leader.prevX;
            double movedZ = leader.getZ() - leader.prevZ;
            boolean moving = movedX * movedX + movedZ * movedZ >= movementDetectionSpeed * movementDetectionSpeed;
            state.movementBlend = approach(state.movementBlend, moving ? 1.0 : 0.0, movementBlendSpeed);

            double leaderJump = jumpLift(leader, follower);
            double followerJump = jumpLift(follower, leader);
            state.followerArmLift = approach(state.followerArmLift, leaderJump, leaderJump > state.followerArmLift ? .18 : .10);
            state.leaderArmLift = approach(state.leaderArmLift, followerJump, followerJump > state.leaderArmLift ? .18 : .10);
        }
    }

    public static double effectiveBehindOffset(Link link) {
        MotionState state = motionByLink.get(link.id());
        double blend = state == null ? 0.0 : state.movementBlend;
        return formationBehindOffset + (movingFormationBehindOffset - formationBehindOffset) * blend;
    }
    public static double leaderArmLift(Link link) {
        MotionState state = motionByLink.get(link.id());
        return state == null ? 0.0 : state.leaderArmLift;
    }
    public static double followerArmLift(Link link) {
        MotionState state = motionByLink.get(link.id());
        return state == null ? 0.0 : state.followerArmLift;
    }
    public static boolean visualsEnabled() { return visualsEnabled; }
    public static double renderDistance() { return renderDistance; }
    public static double formationSideOffset() { return formationSideOffset; }
    public static double formationBehindOffset() { return formationBehindOffset; }
    public static double formationTolerance() { return formationTolerance; }
    public static double formationSpringStrength() { return formationSpringStrength; }
    public static double formationAcceleration() { return formationAcceleration; }
    public static double formationMaxCorrectionSpeed() { return formationMaxCorrectionSpeed; }
    public static double leaderVelocityMatch() { return leaderVelocityMatch; }
    public static double stepAssistVelocity() { return stepAssistVelocity; }
    public static double visualHandGap() { return visualHandGap; }
    public static double maxVisualPositionCorrection() { return maxVisualPositionCorrection; }
    public static void clear() { links = Map.of(); motionByLink.clear(); }
    private static double jumpLift(AbstractClientPlayerEntity jumper, AbstractClientPlayerEntity other) {
        double verticalVelocity = Math.max(0.0, jumper.getVelocity().y);
        boolean airborneJump = !jumper.isOnGround() && (jumper.getY() > other.getY() || verticalVelocity > .04);
        if (!airborneJump) return 0.0;
        return Math.min(maxJumpArmLift, Math.max(jumper.getY() - other.getY(), verticalVelocity * 1.15));
    }
    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
    private static final class MotionState {
        private double movementBlend;
        private double leaderArmLift;
        private double followerArmLift;
    }
    private ClientConnectionCache() {}
}

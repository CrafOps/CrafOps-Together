package net.ppekkungz.crafops.client.cache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.phys.Vec3;
import net.ppekkungz.crafops.interaction.PartnerState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.ppekkungz.crafops.interaction.FollowSteering;
import net.ppekkungz.crafops.interaction.FormationSettings;

import java.util.*;

/**
 * Complete client mirror of the server's links, plus per-link motion state that every client advances
 * identically each tick (formation yaw, walking blend, jump lift) and that renderers interpolate.
 */
public final class ClientConnectionCache {
    public record Link(UUID id, UUID leader, UUID follower) {}
    private static final double TELEPORT_RESET_DISTANCE = 4.0;
    private static final float TURN_CATCH_UP = 0.3f;
    private static final long STALE_NANOS = 200_000_000L;
    /** Measured in game: without this bias the follower ran ~1 tick ahead of the leader (it already moves a tick after steering). */
    private static final double LATENCY_BIAS_TICKS = -0.5;
    private static final int MAX_TRUSTED_PING_MILLIS = 500;
    private static final double SNAP_DISTANCE = 3.0;
    private static final double DISPLAY_SMOOTHING_PER_SECOND = 25.0;
    private static final Map<UUID, Track> partnerTracks = new HashMap<>();
    private static final Map<UUID, Display> displayed = new HashMap<>();
    private record Track(PartnerState state, long receivedNanos) {}
    private record Display(Vec3 position, long atNanos) {}

    private static Map<UUID, Link> links = Map.of();
    private static Map<UUID, Link> incomingByFollower = Map.of();
    private static Map<UUID, Link> outgoingByLeader = Map.of();
    private static FormationSettings settings = FormationSettings.DEFAULTS;
    private static Set<UUID> flying = Set.of();
    private static final Map<UUID, MotionState> motionByLink = new HashMap<>();

    public static void replace(Collection<Link> snapshot) {
        Map<UUID, Link> next = new LinkedHashMap<>(), incoming = new HashMap<>(), outgoing = new HashMap<>();
        for (Link link : snapshot) {
            next.put(link.id(), link);
            incoming.put(link.follower(), link);
            outgoing.put(link.leader(), link);
        }
        links = Collections.unmodifiableMap(next);
        incomingByFollower = incoming;
        outgoingByLeader = outgoing;
        motionByLink.keySet().retainAll(next.keySet());
    }

    public static Collection<Link> getLinks() { return links.values(); }
    public static boolean isConnected(UUID player) { return hasIncoming(player) || hasOutgoing(player); }
    public static boolean isAdjacent(UUID first, UUID second) {
        Link a = incomingByFollower.get(first), b = incomingByFollower.get(second);
        return (a != null && a.leader().equals(second)) || (b != null && b.leader().equals(first));
    }
    public static boolean hasIncoming(UUID player) { return incomingByFollower.containsKey(player); }
    public static boolean hasOutgoing(UUID player) { return outgoingByLeader.containsKey(player); }
    public static Link incomingLink(UUID player) { return incomingByFollower.get(player); }
    public static Link outgoingLink(UUID player) { return outgoingByLeader.get(player); }

    /** Latest directly-relayed state of a holding player, see {@link PartnerState}. */
    public static void receivePartnerState(UUID player, PartnerState state) {
        partnerTracks.put(player, new Track(state, System.nanoTime()));
    }

    /** The relayed state if it is recent enough to trust; otherwise callers fall back to vanilla positions. */
    public static PartnerState freshState(UUID player) {
        Track track = partnerTracks.get(player);
        return track == null || System.nanoTime() - track.receivedNanos > STALE_NANOS ? null : track.state;
    }

    /**
     * Where a remote holding player is right now: their own reported position, carried forward by their
     * velocity for the time since it arrived plus the one-way trip (half of each side's ping
     * of server wait, minus the tick the follower spends moving). Null when no fresh state exists.
     */
    public static Vec3 predictedPosition(UUID player) {
        Track track = partnerTracks.get(player);
        if (track == null || System.nanoTime() - track.receivedNanos > STALE_NANOS) return null;
        double ageTicks = (System.nanoTime() - track.receivedNanos) / 50_000_000.0;
        double tripTicks = (pingMillis(player) + pingMillis(localPlayerId())) / 2.0 / 50.0 + LATENCY_BIAS_TICKS;
        double[] p = track.state.extrapolate(ageTicks + tripTicks);
        return new Vec3(p[0], p[1], p[2]);
    }

    /**
     * {@link #predictedPosition} smoothed per frame, for drawing: a new packet nudges the model instead of
     * snapping it. Large jumps (teleports) snap straight to the new spot.
     */
    public static Vec3 displayPosition(UUID player) {
        Vec3 target = predictedPosition(player);
        if (target == null) { displayed.remove(player); return null; }
        long now = System.nanoTime();
        Display shown = displayed.get(player);
        if (shown == null || shown.position.distanceToSqr(target) > SNAP_DISTANCE * SNAP_DISTANCE) {
            displayed.put(player, new Display(target, now));
            return target;
        }
        double seconds = (now - shown.atNanos) / 1.0e9;
        if (seconds <= 0) return shown.position;
        double blend = 1.0 - Math.exp(-seconds * DISPLAY_SMOOTHING_PER_SECOND);
        Vec3 next = shown.position.lerp(target, blend);
        displayed.put(player, new Display(next, now));
        return next;
    }

    /** Leader velocity in blocks/tick: the relayed one when fresh, else estimated from interpolated movement. */
    public static Vec3 leaderVelocity(Link link) {
        PartnerState state = freshState(link.leader());
        if (state != null) return new Vec3(state.velocityX(), state.velocityY(), state.velocityZ());
        return new Vec3(leaderVelocityX(link), leaderVelocityY(link), leaderVelocityZ(link));
    }

    private static int pingMillis(UUID player) {
        var handler = Minecraft.getInstance().getConnection();
        PlayerInfo entry = handler == null || player == null ? null : handler.getPlayerInfo(player);
        return entry == null ? 0 : Math.min(entry.getLatency(), MAX_TRUSTED_PING_MILLIS);
    }
    private static UUID localPlayerId() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : player.getUUID();
    }

    /** Server-reported flight, since a client cannot see other players' abilities. */
    public static void setFlying(Set<UUID> players) { flying = Set.copyOf(players); }
    public static boolean isFlying(UUID player) { return flying.contains(player); }

    public static void setSettings(FormationSettings next) { settings = next; }
    public static FormationSettings settings() { return settings; }

    /** Advances shared visual/motion state once per client tick. */
    public static void tickMotion(ClientLevel world) {
        for (Link link : links.values()) {
            if (!(world.getPlayerByUUID(link.leader()) instanceof AbstractClientPlayer leader)
                    || !(world.getPlayerByUUID(link.follower()) instanceof AbstractClientPlayer follower)) continue;
            MotionState state = motionByLink.get(link.id());
            double movedX = leader.getX() - leader.xo, movedZ = leader.getZ() - leader.zo;
            if (state == null || movedX * movedX + movedZ * movedZ > TELEPORT_RESET_DISTANCE * TELEPORT_RESET_DISTANCE) {
                state = new MotionState(leader.yBodyRot);
                motionByLink.put(link.id(), state);
                continue;
            }

            state.prevYaw = state.yaw;
            // Ease toward the leader's facing: a fixed floor for small corrections, faster for sharp turns.
            PartnerState relayed = freshState(link.leader());
            float targetYaw = relayed == null ? leader.yBodyRot : relayed.bodyYaw();
            float gap = Math.abs(FollowSteering.wrapDegrees(targetYaw - state.yaw));
            state.yaw = FollowSteering.approachAngle(state.yaw, targetYaw, Math.max((float) settings.yawSpeed(), gap * TURN_CATCH_UP));

            state.velocityX = state.velocityX * 0.5 + movedX * 0.5;
            state.velocityZ = state.velocityZ * 0.5 + movedZ * 0.5;
            state.velocityY = state.velocityY * 0.5 + (leader.getY() - leader.yo) * 0.5;
            double threshold = settings.movementDetectionSpeed();
            boolean moving = movedX * movedX + movedZ * movedZ >= threshold * threshold;
            state.prevBlend = state.blend;
            state.blend = approach(state.blend, moving ? 1.0 : 0.0, settings.movementBlendSpeed());

            double leaderJump = jumpLift(leader, follower), followerJump = jumpLift(follower, leader);
            state.prevFollowerLift = state.followerLift;
            state.prevLeaderLift = state.leaderLift;
            state.followerLift = approach(state.followerLift, leaderJump, leaderJump > state.followerLift ? .18 : .10);
            state.leaderLift = approach(state.leaderLift, followerJump, followerJump > state.leaderLift ? .18 : .10);
        }
    }

    /** Smoothed facing the pair walks along. Turning in place no longer whips the follower around. */
    public static float formationYaw(Link link, float delta, float fallback) {
        MotionState state = motionByLink.get(link.id());
        if (state == null) return fallback;
        return state.prevYaw + FollowSteering.wrapDegrees(state.yaw - state.prevYaw) * delta;
    }
    public static double behindOffset(Link link, float delta) {
        MotionState state = motionByLink.get(link.id());
        double blend = state == null ? 0.0 : smoothstep(lerp(state.prevBlend, state.blend, delta));
        return settings.behindOffset() + (settings.movingBehindOffset() - settings.behindOffset()) * blend;
    }
    public static double leaderArmLift(Link link, float delta) {
        MotionState state = motionByLink.get(link.id());
        return state == null ? 0.0 : lerp(state.prevLeaderLift, state.leaderLift, delta);
    }
    public static double followerArmLift(Link link, float delta) {
        MotionState state = motionByLink.get(link.id());
        return state == null ? 0.0 : lerp(state.prevFollowerLift, state.followerLift, delta);
    }
    /** Leader velocity in blocks/tick, smoothed to hide the 3-tick interpolation steps of remote players. */
    public static double leaderVelocityX(Link link) { MotionState s = motionByLink.get(link.id()); return s == null ? 0 : s.velocityX; }
    public static double leaderVelocityZ(Link link) { MotionState s = motionByLink.get(link.id()); return s == null ? 0 : s.velocityZ; }
    public static double leaderVelocityY(Link link) { MotionState s = motionByLink.get(link.id()); return s == null ? 0 : s.velocityY; }

    public static void clear() {
        links = Map.of(); incomingByFollower = Map.of(); outgoingByLeader = Map.of(); motionByLink.clear(); flying = Set.of();
        partnerTracks.clear(); displayed.clear();
    }

    private static double jumpLift(AbstractClientPlayer jumper, AbstractClientPlayer other) {
        if (jumper.isInWater()) return 0.0;
        double verticalVelocity = Math.max(0.0, jumper.getY() - jumper.yo);
        boolean airborneJump = !jumper.onGround() && (jumper.getY() > other.getY() || verticalVelocity > .04);
        if (!airborneJump) return 0.0;
        return Math.min(settings.maxJumpArmLift(), Math.max(jumper.getY() - other.getY(), verticalVelocity * 1.15));
    }
    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
    private static double lerp(double a, double b, float t) { return a + (b - a) * t; }
    private static double smoothstep(double t) { return t * t * (3 - 2 * t); }

    private static final class MotionState {
        float prevYaw, yaw;
        double prevBlend, blend;
        double prevLeaderLift, leaderLift, prevFollowerLift, followerLift;
        double velocityX, velocityY, velocityZ;
        MotionState(float yaw) { this.prevYaw = yaw; this.yaw = yaw; }
    }
    private ClientConnectionCache() {}
}

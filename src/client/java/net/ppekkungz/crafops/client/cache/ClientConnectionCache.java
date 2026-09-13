package net.ppekkungz.crafops.client.cache;

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

    public static void replace(Collection<Link> snapshot) {
        Map<UUID, Link> next = new LinkedHashMap<>();
        for (Link link : snapshot) next.put(link.id(), link);
        links = Collections.unmodifiableMap(next);
    }
    public static Collection<Link> getLinks() { return links.values(); }
    public static boolean isConnected(UUID player) { return hasIncoming(player) || hasOutgoing(player); }
    public static boolean isAdjacent(UUID first, UUID second) { return links.values().stream().anyMatch(l ->
            (l.leader().equals(first) && l.follower().equals(second)) || (l.leader().equals(second) && l.follower().equals(first))); }
    public static boolean hasIncoming(UUID player) { return links.values().stream().anyMatch(l -> l.follower().equals(player)); }
    public static boolean hasOutgoing(UUID player) { return links.values().stream().anyMatch(l -> l.leader().equals(player)); }
    public static UUID incomingLeader(UUID player) {
        for (Link link : links.values()) if (link.follower().equals(player)) return link.leader();
        return null;
    }
    public static UUID outgoingFollower(UUID player) {
        for (Link link : links.values()) if (link.leader().equals(player)) return link.follower();
        return null;
    }
    public static void setSettings(boolean enabled, double distance, double side, double behind, double tolerance,
                                   double spring, double acceleration, double maxSpeed, double velocityMatch, double stepAssist) {
        visualsEnabled = enabled; renderDistance = distance;
        formationSideOffset = side; formationBehindOffset = behind; formationTolerance = tolerance;
        formationSpringStrength = spring; formationAcceleration = acceleration;
        formationMaxCorrectionSpeed = maxSpeed; leaderVelocityMatch = velocityMatch; stepAssistVelocity = stepAssist;
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
    public static void clear() { links = Map.of(); }
    private ClientConnectionCache() {}
}

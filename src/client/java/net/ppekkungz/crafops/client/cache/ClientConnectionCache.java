package net.ppekkungz.crafops.client.cache;

import java.util.*;

/**
 * ClientConnectionCache — Client-side mirror of all active hand-hold connections.
 *
 * The server is the single source of truth.
 * This cache is populated exclusively from incoming S2C packets
 * and is read every frame by HandConnectionRenderer.
 *
 * Thread safety:
 *   All writes must be dispatched via client.execute() (main thread).
 *   Reads happen on the render thread — in Minecraft 1.20.1 the render
 *   thread and client tick thread share the same main thread, so this is safe.
 *
 * Storage structure:
 *   Symmetric map — both directions stored per connection.
 *   Example: Player A holds Player B's hand →
 *     connectionMap: { A→B, B→A }
 *
 *   This lets the renderer look up a partner from either UUID in O(1).
 */
public class ClientConnectionCache {

    /**
     * Maps each connected player UUID → their partner's UUID.
     * Always stored symmetrically (both directions).
     */
    private static final Map<UUID, UUID> connectionMap = new HashMap<>();

    // ── Write Operations ──────────────────────────────────────────────────────

    /**
     * Register a new connection between two players.
     * Stores both directions so either participant can query their partner.
     *
     * Safe to call even if a previous connection existed —
     * it will simply overwrite with the new partner.
     *
     * @param initiatorUuid UUID of the player who initiated the hand-hold
     * @param targetUuid    UUID of the player being held
     */
    public static void addConnection(UUID initiatorUuid, UUID targetUuid) {
        connectionMap.put(initiatorUuid, targetUuid);
        connectionMap.put(targetUuid, initiatorUuid);
    }

    /**
     * Remove the connection involving the given player.
     * Also removes the symmetric reverse entry automatically.
     *
     * Safe to call even if the player has no active connection.
     *
     * @param playerUuid UUID of either participant in the connection
     */
    public static void removeConnection(UUID playerUuid) {
        UUID partnerUuid = connectionMap.remove(playerUuid);
        if (partnerUuid != null) {
            connectionMap.remove(partnerUuid);
        }
    }

    /**
     * Clear the entire cache.
     * Called when the client disconnects from a world/server.
     */
    public static void clear() {
        connectionMap.clear();
    }

    // ── Read Operations ───────────────────────────────────────────────────────

    /**
     * Returns true if this player is currently in an active connection.
     *
     * @param uuid UUID to check
     */
    public static boolean isConnected(UUID uuid) {
        return connectionMap.containsKey(uuid);
    }

    /**
     * Returns the UUID of the given player's hand-hold partner.
     * Returns null if the player has no active connection.
     *
     * @param uuid UUID of one participant
     */
    public static UUID getPartnerUuid(UUID uuid) {
        return connectionMap.get(uuid);
    }

    /**
     * Returns all unique connection pairs as a list of UUID arrays.
     * Each pair is represented as [initiatorUuid, targetUuid].
     * Each unique connection appears exactly ONCE (not twice).
     *
     * Used by HandConnectionRenderer to iterate and draw lines
     * without rendering the same connection twice.
     *
     * @return list of [UUID, UUID] pairs
     */
    public static List<UUID[]> getAllConnectionPairs() {
        Set<UUID> seen = new HashSet<>();
        List<UUID[]> pairs = new ArrayList<>();

        for (Map.Entry<UUID, UUID> entry : connectionMap.entrySet()) {
            UUID a = entry.getKey();
            UUID b = entry.getValue();

            // Skip if either UUID was already included in a previous pair
            if (seen.contains(a) || seen.contains(b)) continue;

            pairs.add(new UUID[]{a, b});
            seen.add(a);
            seen.add(b);
        }

        return pairs;
    }

    /**
     * Returns the total number of unique active connections.
     * connectionMap.size() / 2 because each pair is stored twice.
     */
    public static int getConnectionCount() {
        return connectionMap.size() / 2;
    }

    // ── Prevent instantiation ─────────────────────────────────────────────────

    private ClientConnectionCache() {}
}
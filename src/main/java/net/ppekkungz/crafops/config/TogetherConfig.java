package net.ppekkungz.crafops.config;

/**
 * Central configuration for the Together interaction system.
 * Future: load/save from a JSON file using Gson or Cloth Config.
 */
public class TogetherConfig {

    // ── Distance thresholds (in blocks) ──────────────────────────────────────

    /** Within this range, no pull is applied. Follower moves freely. */
    public static double comfortRange = 2.0;

    /** Beyond this distance, a soft pull begins. */
    public static double softPullDistance = 2.0;

    /** Beyond this distance, pull strength increases significantly. */
    public static double strongPullDistance = 4.0;

    /**
     * Beyond this distance, the connection is automatically severed.
     * Must be > strongPullDistance.
     */
    public static double maxConnectionDistance = 8.0;

    // ── Pull strength ─────────────────────────────────────────────────────────

    /**
     * Base velocity multiplier applied when soft pull begins.
     * Higher = snappier follow. Keep between 0.05–0.15 for smooth feel.
     */
    public static double softPullStrength = 0.08;

    /**
     * Velocity multiplier used in the strong pull zone.
     * Scales up linearly with distance beyond strongPullDistance.
     */
    public static double strongPullStrength = 0.18;

    /**
     * Maximum velocity magnitude applied per tick to the follower.
     * Prevents teleport-like snapping at extreme distances.
     */
    public static double maxPullVelocity = 0.6;

    // ── Feature toggles ───────────────────────────────────────────────────────

    /** Render the visual hand-hold line on the client. */
    public static boolean enableVisualConnection = true;

    /** Play sound effects on connect/disconnect. */
    public static boolean enableSoundEffects = true;

    // ── Tick rate ─────────────────────────────────────────────────────────────

    /**
     * How often (in ticks) the server checks distances and applies pull.
     * 1 = every tick (smoothest). 2 = every other tick (lighter on CPU).
     */
    public static int pullTickInterval = 1;
}
package net.ppekkungz.crafops.client.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ArmAnimationState — Tracks smooth arm raise progress per player.
 *
 * Instead of snapping the arm instantly, we store a progress value
 * per player UUID (0.0 = arm down, 1.0 = arm fully raised) and
 * interpolate it every frame for smooth animation.
 *
 * Updated every client tick via ClientTickEvents.
 */
public class ArmAnimationState {

    /** How fast the arm raises/lowers per tick. 0.08 = ~12 ticks to full raise. */
    private static final float RAISE_SPEED  = 0.08f;
    private static final float LOWER_SPEED  = 0.10f;

    /** Maps player UUID → current arm raise progress [0.0, 1.0] */
    private static final Map<UUID, Float> progressMap = new HashMap<>();

    // ── Tick Update ───────────────────────────────────────────────────────────

    /**
     * Called every client tick from CrafOpsUtilsClient.
     * Smoothly moves progress toward target (1.0 if connected, 0.0 if not).
     *
     * @param uuid       Player UUID to update
     * @param connected  Whether this player is currently hand-holding
     */
    public static void tick(UUID uuid, boolean connected) {
        float current = progressMap.getOrDefault(uuid, 0.0f);

        if (connected) {
            current = Math.min(1.0f, current + RAISE_SPEED);
        } else {
            current = Math.max(0.0f, current - LOWER_SPEED);
            // Remove entry once fully lowered to save memory
            if (current <= 0.0f) {
                progressMap.remove(uuid);
                return;
            }
        }

        progressMap.put(uuid, current);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Get the current arm raise progress for a player.
     * Returns 0.0 if the player has no active animation.
     */
    public static float getProgress(UUID uuid) {
        return progressMap.getOrDefault(uuid, 0.0f);
    }

    /**
     * Clear all animation states — called on world disconnect.
     */
    public static void clear() {
        progressMap.clear();
    }

    private ArmAnimationState() {}
}
package net.ppekkungz.crafops.interaction;

import java.util.UUID;

/**
 * Represents a single active hand-holding connection between two players.
 *
 * This is the data class — it holds state only.
 * All logic lives in InteractionManager.
 *
 * One component = one directional link:
 *   initiator → holds hand of → target
 *
 * Both players store a reference in InteractionManager so lookups
 * work from either side.
 */
public class HandHoldComponent {

    // ── Identity ──────────────────────────────────────────────────────────────

    /** UUID of the player who initiated the hand-hold (right-clicked). */
    private final UUID initiatorUuid;

    /** UUID of the player whose hand is being held. */
    private final UUID targetUuid;

    // ── State ─────────────────────────────────────────────────────────────────

    /**
     * The interaction type this component represents.
     * Always HAND_HOLD for this class, but stored explicitly so
     * InteractionManager can dispatch generically in the future.
     */
    private final InteractionType type;

    /** Tick timestamp when this connection was established (server world time). */
    private final long connectedAtTick;

    /**
     * Whether this component is still active.
     * Set to false before removal — allows cleanup listeners to detect teardown.
     */
    private boolean active;

    // ── Constructor ───────────────────────────────────────────────────────────

    public HandHoldComponent(UUID initiatorUuid, UUID targetUuid, long connectedAtTick) {
        this.initiatorUuid    = initiatorUuid;
        this.targetUuid       = targetUuid;
        this.type             = InteractionType.HAND_HOLD;
        this.connectedAtTick  = connectedAtTick;
        this.active           = true;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getInitiatorUuid() {
        return initiatorUuid;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public InteractionType getType() {
        return type;
    }

    public long getConnectedAtTick() {
        return connectedAtTick;
    }

    public boolean isActive() {
        return active;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Mark this component as inactive.
     * Called by InteractionManager before removing from its maps.
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Returns true if the given UUID is either participant in this connection.
     */
    public boolean involves(UUID uuid) {
        return initiatorUuid.equals(uuid) || targetUuid.equals(uuid);
    }

    /**
     * Given one participant's UUID, returns the UUID of the other participant.
     * Returns null if the given UUID is not part of this connection.
     */
    public UUID getOtherUuid(UUID uuid) {
        if (initiatorUuid.equals(uuid)) return targetUuid;
        if (targetUuid.equals(uuid))    return initiatorUuid;
        return null;
    }

    @Override
    public String toString() {
        return String.format("HandHoldComponent{%s → %s, active=%b, tick=%d}",
                initiatorUuid, targetUuid, active, connectedAtTick);
    }
}
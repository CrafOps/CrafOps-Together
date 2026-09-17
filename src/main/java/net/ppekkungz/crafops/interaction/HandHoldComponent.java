package net.ppekkungz.crafops.interaction;

import java.util.UUID;

/** Immutable directed edge in a hand-holding chain. */
public final class HandHoldComponent {
    private final UUID id;
    private final UUID leaderUuid;
    private final UUID followerUuid;
    private final long connectedAtTick;
    private double movementBlend;

    public HandHoldComponent(UUID leaderUuid, UUID followerUuid, long tick) {
        this(UUID.randomUUID(), leaderUuid, followerUuid, tick);
    }
    public HandHoldComponent(UUID id, UUID leaderUuid, UUID followerUuid, long tick) {
        this.id = id; this.leaderUuid = leaderUuid; this.followerUuid = followerUuid; this.connectedAtTick = tick;
    }
    public UUID getId() { return id; }
    public UUID getLeaderUuid() { return leaderUuid; }
    public UUID getFollowerUuid() { return followerUuid; }
    public UUID getInitiatorUuid() { return leaderUuid; }
    public UUID getTargetUuid() { return followerUuid; }
    public long getConnectedAtTick() { return connectedAtTick; }
    public InteractionType getType() { return InteractionType.HAND_HOLD; }
    public double getMovementBlend() { return movementBlend; }
    public void updateMovementBlend(boolean moving, double step) {
        movementBlend = moving ? Math.min(1.0, movementBlend + step) : Math.max(0.0, movementBlend - step);
    }
    public boolean involves(UUID uuid) { return leaderUuid.equals(uuid) || followerUuid.equals(uuid); }
    public UUID getOtherUuid(UUID uuid) { return leaderUuid.equals(uuid) ? followerUuid : followerUuid.equals(uuid) ? leaderUuid : null; }
    @Override public String toString() { return leaderUuid + " -> " + followerUuid; }
}

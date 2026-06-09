package net.ppekkungz.crafops.interaction;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.ppekkungz.crafops.config.TogetherConfig;
import net.ppekkungz.crafops.network.TogetherPackets;

import java.util.*;

/**
 * InteractionManager — Central server-side authority for all player interactions.
 *
 * Responsibilities:
 *  - Track all active HandHoldComponent connections
 *  - Handle connect / disconnect requests
 *  - Run per-tick distance checks and velocity-based pull
 *  - Notify clients of state changes via TogetherPackets
 *
 * Architecture note:
 *  Two maps are maintained for O(1) lookup from either participant:
 *    initiatorMap : initiatorUUID → component
 *    targetMap    : targetUUID    → component
 *
 *  Both maps point to the SAME component object.
 *  Removal always touches both maps.
 */
public class InteractionManager {

    // ── Singleton ─────────────────────────────────────────────────────────────
    /** Track last tick that rotation sync was sent, per target UUID. */
    private final Map<UUID, Long> lastRotationSyncTick = new HashMap<>();

    private static final InteractionManager INSTANCE = new InteractionManager();

    public static InteractionManager getInstance() {
        return INSTANCE;
    }

    private InteractionManager() {}

    // ── State ─────────────────────────────────────────────────────────────────

    /**
     * Maps initiator UUID → active component.
     * Used to check "is this player currently holding someone's hand?"
     */
    private final Map<UUID, HandHoldComponent> initiatorMap = new HashMap<>();

    /**
     * Maps target UUID → active component.
     * Used to check "is this player currently being held?"
     */
    private final Map<UUID, HandHoldComponent> targetMap = new HashMap<>();

    /**
     * Components queued for removal at end of tick.
     * Avoids ConcurrentModificationException during tick iteration.
     */
    private final List<HandHoldComponent> pendingRemovals = new ArrayList<>();

    // ── Connection Management ─────────────────────────────────────────────────

    /**
     * Attempt to create a hand-hold connection between two players.
     *
     * Rules:
     *  - Neither player may already be in a connection
     *  - initiator != target
     *  - Both players must be in the same dimension
     *
     * @return true if connection was successfully established
     */
    public boolean connect(ServerPlayerEntity initiator, ServerPlayerEntity target) {

        // Cannot hold your own hand
        if (initiator.getUuid().equals(target.getUuid())) {
            return false;
        }

        // Both players must be in the same world
        if (!initiator.getWorld().equals(target.getWorld())) {
            return false;
        }

        // Neither player may already be connected
        if (isConnected(initiator.getUuid()) || isConnected(target.getUuid())) {
            return false;
        }

        long currentTick = initiator.getWorld().getTime();
        HandHoldComponent component = new HandHoldComponent(
                initiator.getUuid(),
                target.getUuid(),
                currentTick
        );

        initiatorMap.put(initiator.getUuid(), component);
        targetMap.put(target.getUuid(), component);

        // Notify both clients so they can render the visual connection
        TogetherPackets.sendConnectPacket(initiator, target);

        // Play connect sound at initiator's position
        if (TogetherConfig.enableSoundEffects) {
            playConnectSound(initiator);
        }

        return true;
    }

    /**
     * Disconnect whatever connection involves this player.
     * Safe to call even if the player has no active connection.
     *
     * @param player     The player triggering the disconnect
     * @param notifyAll  Whether to send disconnect packets to both participants
     */
    public void disconnect(ServerPlayerEntity player, boolean notifyAll) {
        HandHoldComponent component = getComponentFor(player.getUuid());
        if (component == null) return;

        removeComponent(component);

        if (notifyAll) {
            // Resolve the other participant and notify both sides
            UUID otherUuid = component.getOtherUuid(player.getUuid());
            ServerPlayerEntity other = player.getServer()
                    .getPlayerManager()
                    .getPlayer(otherUuid);

            TogetherPackets.sendDisconnectPacket(player);
            if (other != null) {
                TogetherPackets.sendDisconnectPacket(other);
            }
        }

        if (TogetherConfig.enableSoundEffects) {
            playDisconnectSound(player);
        }
    }

    /**
     * Internal removal — marks component inactive and removes from both maps.
     */
    private void removeComponent(HandHoldComponent component) {
        component.deactivate();
        initiatorMap.remove(component.getInitiatorUuid());
        targetMap.remove(component.getTargetUuid());
        lastRotationSyncTick.remove(component.getTargetUuid());
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns true if this player is in any active connection
     * (either as initiator or target).
     */
    public boolean isConnected(UUID uuid) {
        return initiatorMap.containsKey(uuid) || targetMap.containsKey(uuid);
    }

    /**
     * Returns the active component for a player, regardless of their role.
     * Returns null if the player has no active connection.
     */
    public HandHoldComponent getComponentFor(UUID uuid) {
        HandHoldComponent c = initiatorMap.get(uuid);
        if (c != null) return c;
        return targetMap.get(uuid);
    }

    /**
     * Returns a read-only view of all active initiator-keyed components.
     * Used by the tick loop and debug commands.
     */
    public Collection<HandHoldComponent> getAllComponents() {
        return Collections.unmodifiableCollection(initiatorMap.values());
    }

    // ── Tick Loop ─────────────────────────────────────────────────────────────

    /**
     * Called every server tick from CrafOpsUtils via ServerTickEvents.
     *
     * For each active connection:
     *  1. Resolve both players from the server's player manager
     *  2. Check they are still online and in the same world
     *  3. Measure distance
     *  4. Auto-disconnect if beyond max range
     *  5. Apply velocity pull if needed
     */
    public void onServerTick(net.minecraft.server.MinecraftServer server) {
        long currentTick = server.getOverworld().getTime();
        if (currentTick % TogetherConfig.pullTickInterval != 0) return;

        for (HandHoldComponent component : initiatorMap.values()) {
            if (!component.isActive()) {
                pendingRemovals.add(component);
                continue;
            }

            ServerPlayerEntity initiator = server.getPlayerManager()
                    .getPlayer(component.getInitiatorUuid());
            ServerPlayerEntity target = server.getPlayerManager()
                    .getPlayer(component.getTargetUuid());

            if (initiator == null || target == null) {
                pendingRemovals.add(component);
                continue;
            }

            if (!initiator.getWorld().equals(target.getWorld())) {
                pendingRemovals.add(component);
                TogetherPackets.sendDisconnectPacket(initiator);
                TogetherPackets.sendDisconnectPacket(target);
                continue;
            }

            // ── No max distance check — follower is always locked to leader ───
            applyPull(initiator, target, 0);
        }

        for (HandHoldComponent c : pendingRemovals) removeComponent(c);
        pendingRemovals.clear();
    }

    // ── Velocity Pull System ──────────────────────────────────────────────────

    /**
     * Lock follower position relative to initiator.
     *
     * Instead of velocity pull, we directly set the follower's position
     * to a fixed offset behind the initiator every tick.
     *
     * Offset: 0.8 blocks behind initiator, same height.
     * This keeps hands touching at all times.
     */
    private void applyPull(ServerPlayerEntity initiator,
                           ServerPlayerEntity target,
                           double distance) {

        // ── Leader's facing direction ─────────────────────────────────────────
        // Minecraft yaw: 0 = south (+Z), 90 = west (-X), 180 = north (-Z), -90 = east (+X)
        // Convert to radians for trig
        float yawDeg = initiator.getYaw();
        float yawRad = (float) Math.toRadians(yawDeg);

        // Forward vector of leader (direction they are FACING)
        // In Minecraft: forward = (-sin(yaw), 0, cos(yaw))
        double fwdX = -Math.sin(yawRad);
        double fwdZ =  Math.cos(yawRad);

        // Follow position = directly BEHIND leader by followDist blocks
        double followDist = 0.8;
        double targetX = initiator.getX() - fwdX * followDist;
        double targetY = initiator.getY();
        double targetZ = initiator.getZ() - fwdZ * followDist;

        // ── Snap follower position toward target ──────────────────────────────
        double dx = targetX - target.getX();
        double dz = targetZ - target.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        if (horizDist > 0.02) {
            double snapSpeed = 0.6;
            target.setVelocity(
                    dx * snapSpeed,
                    target.getVelocity().y,
                    dz * snapSpeed
            );
            target.velocityModified = true;
        } else {
            // Close enough — zero out horizontal velocity to prevent jitter
            target.setVelocity(0, target.getVelocity().y, 0);
            target.velocityModified = true;
        }

// ── Force follower to face same direction as leader ───────────────────
        target.setYaw(yawDeg);
        target.setHeadYaw(yawDeg);
        target.bodyYaw = yawDeg;
        target.prevYaw = yawDeg;
        target.prevBodyYaw = yawDeg;

// ── Throttle requestTeleport — ส่งทุก 2 tick เพื่อป้องกัน กระตุก ────────
        long now = initiator.getServerWorld().getTime();
        long lastSync = lastRotationSyncTick.getOrDefault(target.getUuid(), 0L);

        if (now - lastSync >= 2) {
            lastRotationSyncTick.put(target.getUuid(), now);
            target.networkHandler.requestTeleport(
                    target.getX(), target.getY(), target.getZ(),
                    yawDeg, target.getPitch()
            );
        }
    }

    // ── Sound Effects ─────────────────────────────────────────────────────────

    private void playConnectSound(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        world.playSound(
                null,
                player.getBlockPos(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                SoundCategory.PLAYERS,
                0.5f,
                1.2f
        );
    }

    private void playDisconnectSound(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        world.playSound(
                null,
                player.getBlockPos(),
                SoundEvents.BLOCK_WOOL_PLACE,
                SoundCategory.PLAYERS,
                0.4f,
                0.9f
        );
    }

    // ── Debug ─────────────────────────────────────────────────────────────────

    /**
     * Returns a human-readable summary of all active connections.
     * Used by /together debug command.
     */
    public String getDebugInfo() {
        if (initiatorMap.isEmpty()) {
            return "No active connections.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Active connections: ").append(initiatorMap.size()).append("\n");
        for (HandHoldComponent c : initiatorMap.values()) {
            sb.append("  ").append(c.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Disconnect ALL active connections.
     * Called on server shutdown to ensure clean state.
     * Sends disconnect packets to all participants before clearing.
     */
    public void disconnectAll(net.minecraft.server.MinecraftServer server) {
        // Copy to avoid ConcurrentModificationException
        List<HandHoldComponent> all = new ArrayList<>(initiatorMap.values());

        for (HandHoldComponent component : all) {
            component.deactivate();

            ServerPlayerEntity initiator = server.getPlayerManager()
                    .getPlayer(component.getInitiatorUuid());
            ServerPlayerEntity target = server.getPlayerManager()
                    .getPlayer(component.getTargetUuid());

            if (initiator != null) TogetherPackets.sendDisconnectPacket(initiator);
            if (target != null)    TogetherPackets.sendDisconnectPacket(target);
        }

        initiatorMap.clear();
        targetMap.clear();
        LOGGER.info("[CrafOps: Together] All connections cleared.");
    }

    // เพิ่ม import logger ที่ด้านบน class
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("InteractionManager");

    /**
     * Returns true if this UUID is the INITIATOR of their connection.
     * Initiator = leader = the one who right-clicked first.
     */
    public boolean isInitiator(UUID uuid) {
        return initiatorMap.containsKey(uuid);
    }
}
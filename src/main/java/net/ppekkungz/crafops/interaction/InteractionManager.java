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

        // Only run every N ticks as configured
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

            // If either player logged off, disconnect
            if (initiator == null || target == null) {
                pendingRemovals.add(component);
                continue;
            }

            // If they changed dimensions, disconnect
            if (!initiator.getWorld().equals(target.getWorld())) {
                pendingRemovals.add(component);
                TogetherPackets.sendDisconnectPacket(initiator);
                TogetherPackets.sendDisconnectPacket(target);
                continue;
            }

            double distance = initiator.getPos().distanceTo(target.getPos());

            // Auto-disconnect beyond max range
            if (distance > TogetherConfig.maxConnectionDistance) {
                pendingRemovals.add(component);
                TogetherPackets.sendDisconnectPacket(initiator);
                TogetherPackets.sendDisconnectPacket(target);
                continue;
            }

            // Apply velocity-based pull to the target
            applyPull(initiator, target, distance);
        }

        // Process removals after iteration to avoid ConcurrentModificationException
        for (HandHoldComponent c : pendingRemovals) {
            removeComponent(c);
        }
        pendingRemovals.clear();
    }

    // ── Velocity Pull System ──────────────────────────────────────────────────

    /**
     * Applies smooth velocity to the target player to pull them toward
     * the initiator when they are outside the comfort range.
     *
     * Pull zones:
     *   0 – comfortRange      : no pull
     *   comfortRange – strong  : soft pull (linear scale)
     *   strong – max           : strong pull (accelerated scale)
     *
     * Key design decisions:
     *  - Uses addVelocity() NOT teleport — no rubber-banding
     *  - Velocity is capped at maxPullVelocity to prevent snapping
     *  - Y-axis pull is dampened to avoid launching the player upward
     *  - Pull direction is normalized so diagonal movement is smooth
     *  - Preserves existing horizontal velocity partially (blending)
     */
    private void applyPull(ServerPlayerEntity initiator,
                           ServerPlayerEntity target,
                           double distance) {

        // No pull within comfort range
        if (distance <= TogetherConfig.comfortRange) return;

        // Direction vector from target → initiator
        Vec3d targetPos    = target.getPos();
        Vec3d initiatorPos = initiator.getPos();
        Vec3d direction    = initiatorPos.subtract(targetPos).normalize();

        // Calculate pull strength based on distance zone
        double pullStrength;

        if (distance <= TogetherConfig.strongPullDistance) {
            // Soft pull zone: linearly interpolate between 0 and softPullStrength
            double t = (distance - TogetherConfig.comfortRange)
                    / (TogetherConfig.strongPullDistance - TogetherConfig.comfortRange);
            pullStrength = t * TogetherConfig.softPullStrength;

        } else {
            // Strong pull zone: scale up beyond softPullStrength
            double t = (distance - TogetherConfig.strongPullDistance)
                    / (TogetherConfig.maxConnectionDistance - TogetherConfig.strongPullDistance);
            // Clamp t to [0, 1]
            t = Math.min(t, 1.0);
            pullStrength = TogetherConfig.softPullStrength
                    + t * (TogetherConfig.strongPullStrength - TogetherConfig.softPullStrength);
        }

        // Cap total pull velocity
        pullStrength = Math.min(pullStrength, TogetherConfig.maxPullVelocity);

        // Build velocity delta
        double vx = direction.x * pullStrength;
        double vy = direction.y * pullStrength * 0.4; // dampen vertical
        double vz = direction.z * pullStrength;

        // Blend with existing velocity to avoid sudden direction changes
        Vec3d currentVel = target.getVelocity();
        double blendFactor = 0.6; // 60% pull, 40% existing momentum

        double finalVx = currentVel.x * (1.0 - blendFactor) + vx * blendFactor;
        double finalVy = currentVel.y; // let vanilla handle Y (gravity, jumping)
        double finalVz = currentVel.z * (1.0 - blendFactor) + vz * blendFactor;

        // Apply and sync to client
        target.setVelocity(finalVx, finalVy, finalVz);
        target.velocityModified = true; // tells the server to sync velocity packet
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
}
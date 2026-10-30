package net.ppekkungz.crafops.client.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.interaction.FollowSteering;
import net.ppekkungz.crafops.interaction.FormationSettings;
import net.ppekkungz.crafops.interaction.PartnerState;

/**
 * Drives the local follower. Player movement is client-authoritative in vanilla, so steering the real
 * movement input here gives natural walking, sprinting, stepping and swimming with no server tug-of-war.
 */
public final class FollowerController {
    private static final double MAX_CATCH_UP_STEP = 0.12;
    private static final double LEADER_PREDICTION_TICKS = 4.0;
    private static final double FLIGHT_VERTICAL_DEADBAND = 0.2;
    private static final double WATER_FOLLOW_GAIN = 0.3;
    private static final double WATER_VERTICAL_GAIN = 0.15;
    private static final double WATER_RESPONSE = 0.6;
    private static final double WATER_MAX_STEP = 0.2;
    private static final double FLIGHT_VERTICAL_GAIN = 0.12;
    private static final double FLIGHT_VERTICAL_LIMIT = 0.12;
    private static boolean takenOffWithLeader;

    /** Called from {@code MovementInputUpdateEvent}, right after the keys are read: replaces them while being led. */
    public static void steer(Input input) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer follower = client.player;
        if (follower == null || follower.input != input || client.level == null) return;
        var link = ClientConnectionCache.incomingLink(follower.getUUID());
        Player leader = link == null ? null : client.level.getPlayerByUUID(link.leader());
        if (leader == null) { takenOffWithLeader = false; return; }

        FormationSettings settings = ClientConnectionCache.settings();
        Vec3 error = goal(leader, link).subtract(follower.position());
        boolean flying = followFlight(follower, ClientConnectionCache.isFlying(leader.getUUID()));
        boolean swimming = !flying && settings.swimTogether() && follower.isInWater() && leader.isInWater();
        double maxSpeed = flying
                ? (follower.isSprinting() ? FollowSteering.SPRINT_FLY_SPEED : FollowSteering.FLY_SPEED)
                : (follower.isSprinting() ? FollowSteering.SPRINT_SPEED : FollowSteering.WALK_SPEED);
        Vec3 leaderVelocity = ClientConnectionCache.leaderVelocity(link);
        PartnerState relayed = ClientConnectionCache.freshState(leader.getUUID());
        FollowSteering.Input steering = FollowSteering.steer(error.x, error.z, leaderVelocity.x, leaderVelocity.z,
                follower.getYRot(), settings.followSpring(), settings.followDeadzone(), maxSpeed);

        float scale = follower.isMovingSlowly() ? (float) follower.getAttributeValue(Attributes.SNEAKING_SPEED) : 1.0f;
        input.forwardImpulse = steering.forward() * scale;
        input.leftImpulse = steering.sideways() * scale;
        input.up = steering.forward() > 0.1f;
        input.down = steering.forward() < -0.1f;
        input.left = steering.sideways() > 0.1f;
        input.right = steering.sideways() < -0.1f;

        boolean leaderSwimming = relayed != null ? relayed.has(PartnerState.SWIMMING) : leader.isSwimming();
        boolean wantsSpeed = (relayed != null ? relayed.has(PartnerState.SPRINTING) : leader.isSprinting()) || steering.demand() > 1.05;
        if (swimming && leaderSwimming && follower.isUnderWater()) {
            // Swim pose follows sprinting; once swimming, vanilla keeps it until leaving the water, so both lie flat together.
            follower.setSprinting(true);
        } else if (follower.isSwimming() && !leaderSwimming) {
            follower.setSprinting(false);
        } else {
            follower.setSprinting(wantsSpeed && steering.forward() > 0.6f && !follower.isShiftKeyDown());
        }

        if (flying) {
            // Space rises and sneak sinks while flying; the dead band keeps the pair from bobbing.
            double verticalError = error.y + leaderVelocity.y * LEADER_PREDICTION_TICKS;
            input.jumping = verticalError > FLIGHT_VERTICAL_DEADBAND;
            input.shiftKeyDown = verticalError < -FLIGHT_VERTICAL_DEADBAND;
        } else if (swimming) {
            input.jumping = error.y > 0.25;
        } else if (follower.onGround()) {
            // With a relayed state the leader's jump key arrives the tick it is pressed, so both leave the ground together.
            // A server-relayed state (fake players, unmodded clients) has no key flag, so fall back to its upward motion.
            boolean leaderJumped = relayed != null
                    ? relayed.has(PartnerState.JUMPING) || !relayed.has(PartnerState.ON_GROUND) && relayed.velocityY() > 0.1f
                            && Math.abs(relayed.y() - follower.getY()) < 0.6
                    : !leader.onGround() && leader.getY() - leader.yo > 0.1 && Math.abs(leader.getY() - follower.getY()) < 0.6;
            boolean blocked = follower.horizontalCollision && steering.demand() > 0.3;
            input.jumping = leaderJumped || blocked || error.y > 0.6;
        }
    }

    /** End of client tick: tops up speed only when walking alone could not hold formation (water, sideways sprint). */
    public static void tickAfterMovement(Minecraft client) {
        LocalPlayer follower = client.player;
        if (follower == null || client.level == null) return;
        var link = ClientConnectionCache.incomingLink(follower.getUUID());
        Player leader = link == null ? null : client.level.getPlayerByUUID(link.leader());
        if (leader == null) return;

        FormationSettings settings = ClientConnectionCache.settings();
        Vec3 error = goal(leader, link).subtract(follower.position());
        Vec3 velocity = follower.getDeltaMovement();
        double addX = 0, addY = 0, addZ = 0;

        Vec3 leaderVelocity = ClientConnectionCache.leaderVelocity(link);
        if (settings.swimTogether() && !follower.getAbilities().flying && follower.isInWater()) {
            // Water drag (x0.8 per tick) caps input-driven speed well below the leader's swim, so input alone trails
            // by a steady gap. In water, steer the velocity itself every tick: match the leader, then close the gap.
            Vec3 desired = new Vec3(leaderVelocity.x + error.x * WATER_FOLLOW_GAIN,
                    leaderVelocity.y + error.y * WATER_VERTICAL_GAIN,
                    leaderVelocity.z + error.z * WATER_FOLLOW_GAIN);
            Vec3 step = desired.subtract(velocity).scale(WATER_RESPONSE);
            follower.push(clamp(step.x, -WATER_MAX_STEP, WATER_MAX_STEP),
                    clamp(step.y, -WATER_MAX_STEP, WATER_MAX_STEP), clamp(step.z, -WATER_MAX_STEP, WATER_MAX_STEP));
            return;
        }
        double horizontal = Math.sqrt(error.x * error.x + error.z * error.z);
        if (horizontal > settings.catchUpThreshold()) {
            double desiredX = leaderVelocity.x + error.x * settings.catchUpStrength();
            double desiredZ = leaderVelocity.z + error.z * settings.catchUpStrength();
            // Only ever push toward the goal; never brake input-driven motion that is already helping.
            if ((desiredX - velocity.x) * error.x > 0) addX = clamp(desiredX - velocity.x, -MAX_CATCH_UP_STEP, MAX_CATCH_UP_STEP);
            if ((desiredZ - velocity.z) * error.z > 0) addZ = clamp(desiredZ - velocity.z, -MAX_CATCH_UP_STEP, MAX_CATCH_UP_STEP);
        }
        if (follower.getAbilities().flying) {
            // Match the leader's climb or descent, then close the remaining height gap.
            double leaderVy = leaderVelocity.y;
            double predictedError = error.y + leaderVy * LEADER_PREDICTION_TICKS;
            double desiredVy = leaderVy + predictedError * FLIGHT_VERTICAL_GAIN * 2;
            addY = clamp(desiredVy - velocity.y, -FLIGHT_VERTICAL_LIMIT, FLIGHT_VERTICAL_LIMIT);
        }
        if (addX != 0 || addY != 0 || addZ != 0) follower.push(addX, addY, addZ);
    }

    /**
     * Takes off when the leader does (only if this player may fly, e.g. creative) and lands with them,
     * but never grounds a player who was already flying on their own before the leader took off.
     */
    private static boolean followFlight(LocalPlayer follower, boolean leaderFlying) {
        var abilities = follower.getAbilities();
        if (leaderFlying && abilities.mayfly && !abilities.flying) {
            abilities.flying = true;
            follower.onUpdateAbilities();
            takenOffWithLeader = true;
        } else if (!leaderFlying && takenOffWithLeader) {
            if (abilities.flying) { abilities.flying = false; follower.onUpdateAbilities(); }
            takenOffWithLeader = false;
        }
        return abilities.flying;
    }

    /** Where the follower should stand: on the leader's right, a touch behind while walking. */
    public static Vec3 goal(Player leader, ClientConnectionCache.Link link) {
        FormationSettings settings = ClientConnectionCache.settings();
        double yaw = Math.toRadians(ClientConnectionCache.formationYaw(link, 1.0f, leader.yBodyRot));
        double forwardX = -Math.sin(yaw), forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        double behind = ClientConnectionCache.behindOffset(link, 1.0f);
        // Aim where the leader actually is now: the relayed state carried forward by the real ping, or, without one,
        // the vanilla position (a few ticks old) pushed ahead by a fixed guess.
        Vec3 base = ClientConnectionCache.predictedPosition(leader.getUUID());
        if (base == null) {
            Vec3 velocity = ClientConnectionCache.leaderVelocity(link);
            base = new Vec3(leader.getX() + velocity.x * LEADER_PREDICTION_TICKS, leader.getY(), leader.getZ() + velocity.z * LEADER_PREDICTION_TICKS);
        }
        return new Vec3(base.x + rightX * settings.sideOffset() - forwardX * behind,
                base.y,
                base.z + rightZ * settings.sideOffset() - forwardZ * behind);
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private FollowerController() {}
}

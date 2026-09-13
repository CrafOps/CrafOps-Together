package net.ppekkungz.crafops.client.movement;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;

/** Mirrors the server constraint for the local follower, eliminating divergent client prediction. */
public final class ClientFormationController {
    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        var follower = client.player;
        var leaderId = ClientConnectionCache.incomingLeader(follower.getUuid());
        if (leaderId == null) return;
        var leader = client.world.getPlayerByUuid(leaderId);
        if (leader == null) return;

        double yaw = Math.toRadians(leader.bodyYaw);
        double forwardX = -Math.sin(yaw), forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        Vec3d goal = new Vec3d(
                leader.getX() + rightX * ClientConnectionCache.formationSideOffset()
                        - forwardX * ClientConnectionCache.formationBehindOffset(),
                leader.getY(),
                leader.getZ() + rightZ * ClientConnectionCache.formationSideOffset()
                        - forwardZ * ClientConnectionCache.formationBehindOffset()
        );
        Vec3d error = goal.subtract(follower.getPos());
        double horizontalError = Math.sqrt(error.x * error.x + error.z * error.z);
        Vec3d followerVelocity = follower.getVelocity();
        Vec3d leaderVelocity = leader.getVelocity();
        double correctionSpeed = horizontalError <= ClientConnectionCache.formationTolerance() ? 0
                : Math.min(ClientConnectionCache.formationMaxCorrectionSpeed(),
                ClientConnectionCache.formationSpringStrength() * horizontalError);
        double correctionX = horizontalError < .0001 ? 0 : error.x / horizontalError * correctionSpeed;
        double correctionZ = horizontalError < .0001 ? 0 : error.z / horizontalError * correctionSpeed;
        double desiredX = leaderVelocity.x * ClientConnectionCache.leaderVelocityMatch() + correctionX;
        double desiredZ = leaderVelocity.z * ClientConnectionCache.leaderVelocityMatch() + correctionZ;
        double addX = clamp(desiredX - followerVelocity.x, -ClientConnectionCache.formationAcceleration(), ClientConnectionCache.formationAcceleration());
        double addZ = clamp(desiredZ - followerVelocity.z, -ClientConnectionCache.formationAcceleration(), ClientConnectionCache.formationAcceleration());
        double addY = follower.isOnGround() && error.y > .45 ? ClientConnectionCache.stepAssistVelocity() : 0;
        follower.addVelocity(addX, addY, addZ);
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private ClientFormationController() {}
}

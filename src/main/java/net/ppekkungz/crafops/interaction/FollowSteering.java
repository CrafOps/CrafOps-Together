package net.ppekkungz.crafops.interaction;

/**
 * Pure steering maths for the follower: turns "where I should be" into vanilla movement input,
 * so the follower genuinely walks (limb swing, step-up, swimming) instead of being shoved.
 */
public final class FollowSteering {
    /** Steady-state ground speeds of a vanilla player in blocks per tick. */
    public static final double WALK_SPEED = 0.2159;
    public static final double SPRINT_SPEED = 0.2806;
    /** Creative flight terminal speeds (blocks per tick). */
    public static final double FLY_SPEED = 0.5;
    public static final double SPRINT_FLY_SPEED = 1.0;

    /** Movement input in the player's own frame; {@code demand} above 1 means input alone cannot keep up. */
    public record Input(float forward, float sideways, double demand) {
        public static final Input NONE = new Input(0f, 0f, 0.0);
    }

    public static Input steer(double errorX, double errorZ, double leaderVelX, double leaderVelZ,
                              float yawDegrees, double spring, double deadzone, double maxSpeed) {
        double errorSq = errorX * errorX + errorZ * errorZ;
        double leaderSq = leaderVelX * leaderVelX + leaderVelZ * leaderVelZ;
        if (errorSq < deadzone * deadzone && leaderSq < 1.0e-4) return Input.NONE;

        double desiredX = leaderVelX + errorX * spring;
        double desiredZ = leaderVelZ + errorZ * spring;
        double speed = Math.sqrt(desiredX * desiredX + desiredZ * desiredZ);
        if (speed < 1.0e-5) return Input.NONE;

        double demand = speed / maxSpeed;
        double scale = Math.min(1.0, demand) / speed;
        double worldX = desiredX * scale, worldZ = desiredZ * scale;
        return toLocal(worldX, worldZ, yawDegrees, demand);
    }

    /** Inverse of vanilla {@code Entity.movementInputToVelocity}: positive sideways is the player's left. */
    public static Input toLocal(double worldX, double worldZ, float yawDegrees, double demand) {
        double yaw = Math.toRadians(yawDegrees);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        float forward = (float) (-worldX * sin + worldZ * cos);
        float sideways = (float) (worldX * cos + worldZ * sin);
        return new Input(forward, sideways, demand);
    }

    /** Rotates {@code current} toward {@code target} by at most {@code maxStep} degrees, the short way round. */
    public static float approachAngle(float current, float target, float maxStep) {
        float difference = wrapDegrees(target - current);
        if (Math.abs(difference) <= maxStep) return target;
        return current + Math.copySign(maxStep, difference);
    }

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    private FollowSteering() {}
}

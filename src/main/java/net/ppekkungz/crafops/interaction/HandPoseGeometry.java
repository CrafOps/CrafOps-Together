package net.ppekkungz.crafops.interaction;

/** Pure vanilla-player arm geometry shared by rendering code and unit tests. */
public final class HandPoseGeometry {
    public static final double SHOULDER_OFFSET = 5.0 / 16.0;
    public static final double ARM_LENGTH = 11.5 / 16.0;
    /** Height difference at which both arms have fully switched from the relaxed hang to the shoulder-to-shoulder line. */
    private static final double HEIGHT_BLEND = 0.8;

    public record ArmAngles(float pitch, float roll) {}

    public static ArmAngles angles(double playerSpacing, double behindOffset, double handGap, boolean rightArm) {
        return angles(playerSpacing, behindOffset, handGap, rightArm, 0.0);
    }

    /**
     * @param verticalLift how much higher the partner is (negative when lower). On level ground both arms hang
     *                     relaxed toward each other; as the height difference grows they swing onto the line between
     *                     the two shoulders, so the upper partner reaches down and the lower one reaches up along the
     *                     same line instead of one arm dangling straight down.
     */
    public static ArmAngles angles(double playerSpacing, double behindOffset, double handGap, boolean rightArm,
                                   double verticalLift) {
        double sign = rightArm ? 1.0 : -1.0;
        double forward = rightArm ? -behindOffset * 0.5 : behindOffset * 0.5;

        double relaxedRight = sign * (playerSpacing * 0.5 - handGap * 0.5 - SHOULDER_OFFSET);
        double relaxedDown = Math.sqrt(Math.max(0.04, ARM_LENGTH * ARM_LENGTH - relaxedRight * relaxedRight - forward * forward));
        // Halfway to the partner's shoulder, from this shoulder.
        double lineRight = sign * (playerSpacing * 0.5 - SHOULDER_OFFSET);
        double lineDown = -verticalLift * 0.5;

        double t = Math.min(1.0, Math.abs(verticalLift) / HEIGHT_BLEND);
        double targetRight = relaxedRight + (lineRight - relaxedRight) * t;
        double down = relaxedDown + (lineDown - relaxedDown) * t;
        double targetForward = forward;
        // ModelPart applies pitch (X) first, then roll (Z); the arm rests pointing down. Solving for a unit aim
        // direction (right, down, forward) gives roll = atan2(right, down) and pitch = -asin(forward), which stays
        // correct when the hand aims above the shoulder (down < 0).
        double length = Math.sqrt(targetRight * targetRight + down * down + targetForward * targetForward);
        float roll = (float) Math.atan2(targetRight, down);
        float pitch = (float) -Math.asin(targetForward / length);
        return new ArmAngles(pitch, roll);
    }

    private HandPoseGeometry() {}
}

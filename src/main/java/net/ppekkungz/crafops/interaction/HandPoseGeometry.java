package net.ppekkungz.crafops.interaction;

/** Pure vanilla-player arm geometry shared by rendering code and unit tests. */
public final class HandPoseGeometry {
    public static final double SHOULDER_OFFSET = 5.0 / 16.0;
    public static final double ARM_LENGTH = 11.5 / 16.0;

    public record ArmAngles(float pitch, float roll) {}

    public static ArmAngles angles(double playerSpacing, double behindOffset, double handGap, boolean rightArm) {
        return angles(playerSpacing, behindOffset, handGap, rightArm, 0.0);
    }

    public static ArmAngles angles(double playerSpacing, double behindOffset, double handGap, boolean rightArm,
                                   double verticalLift) {
        double sign = rightArm ? 1.0 : -1.0;
        double targetRight = sign * (playerSpacing * 0.5 - handGap * 0.5 - SHOULDER_OFFSET);
        double targetForward = rightArm ? -behindOffset * 0.5 : behindOffset * 0.5;
        double horizontalSq = targetRight * targetRight + targetForward * targetForward;
        double naturalDown = Math.sqrt(Math.max(0.04, ARM_LENGTH * ARM_LENGTH - horizontalSq));
        double down = Math.max(-0.15, naturalDown - Math.max(0.0, verticalLift));
        float roll = (float) Math.atan2(targetRight, Math.sqrt(down * down + targetForward * targetForward));
        float pitch = (float) -Math.atan2(targetForward, down);
        return new ArmAngles(pitch, roll);
    }

    private HandPoseGeometry() {}
}

package net.ppekkungz.crafops.interaction;

/** Render-only head rotation limits that preserve camera freedom without neck wrapping. */
public final class HeadPoseGeometry {
    public static float relativeYaw(float headYaw, float bodyYaw, float maximumDegrees) {
        float wrapped = wrapDegrees(headYaw - bodyYaw);
        return Math.max(-maximumDegrees, Math.min(maximumDegrees, wrapped));
    }

    public static float clampRelativeYaw(float relativeYaw, float maximumDegrees) {
        float wrapped = wrapDegrees(relativeYaw);
        return Math.max(-maximumDegrees, Math.min(maximumDegrees, wrapped));
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;

        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }

        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }

        return wrapped;
    }

    private HeadPoseGeometry() {
    }
}

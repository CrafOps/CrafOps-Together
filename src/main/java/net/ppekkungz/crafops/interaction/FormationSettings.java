package net.ppekkungz.crafops.interaction;

import net.minecraft.network.FriendlyByteBuf;

/** The slice of server config every client needs to drive and draw a hand-hold identically. */
public record FormationSettings(
        double sideOffset,
        double behindOffset,
        double movingBehindOffset,
        double movementDetectionSpeed,
        double movementBlendSpeed,
        double yawSpeed,
        double followSpring,
        double followDeadzone,
        double catchUpThreshold,
        double catchUpStrength,
        boolean swimTogether,
        double handGap,
        double maxJumpArmLift,
        double armSway,
        double maxVisualCorrection) {

    public static final FormationSettings DEFAULTS = new FormationSettings(1.04, 0.0, 0.08, 0.025, 0.12, 14.0,
            0.35, 0.06, 0.3, 0.22, true, 0.08, 0.55, 0.12, 1.25);

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(sideOffset); buf.writeDouble(behindOffset); buf.writeDouble(movingBehindOffset);
        buf.writeDouble(movementDetectionSpeed); buf.writeDouble(movementBlendSpeed); buf.writeDouble(yawSpeed);
        buf.writeDouble(followSpring); buf.writeDouble(followDeadzone);
        buf.writeDouble(catchUpThreshold); buf.writeDouble(catchUpStrength); buf.writeBoolean(swimTogether);
        buf.writeDouble(handGap); buf.writeDouble(maxJumpArmLift); buf.writeDouble(armSway);
        buf.writeDouble(maxVisualCorrection);
    }

    public static FormationSettings read(FriendlyByteBuf buf) {
        return new FormationSettings(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readBoolean(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble());
    }
}

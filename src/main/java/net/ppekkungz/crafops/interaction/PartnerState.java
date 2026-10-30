package net.ppekkungz.crafops.interaction;

import net.minecraft.network.PacketByteBuf;

/**
 * One tick of a holding player's own movement, sent by their client and relayed straight to partners.
 * Skips the vanilla entity tracker (sent every 2 ticks) and remote interpolation (3 ticks), so partners see
 * each other about half a ping late instead of ~150-300 ms late.
 */
public record PartnerState(double x, double y, double z, float velocityX, float velocityY, float velocityZ,
                           float bodyYaw, byte flags) {
    public static final byte ON_GROUND = 1, IN_WATER = 2, FLYING = 4, SPRINTING = 8, JUMPING = 16, SWIMMING = 32;
    /** Never extrapolate further than this; beyond it the guess is worse than waiting for the next packet. */
    public static final double MAX_EXTRAPOLATION_TICKS = 6.0;

    public boolean has(byte flag) { return (flags & flag) != 0; }

    /** Dead reckoning: where this player most likely is {@code ticks} after the state was captured. */
    public double[] extrapolate(double ticks) {
        double t = Math.max(0.0, Math.min(MAX_EXTRAPOLATION_TICKS, ticks));
        // On the ground vertical velocity is just gravity against the floor; extrapolating it sinks the player.
        double vy = has(ON_GROUND) ? 0.0 : velocityY;
        return new double[]{x + velocityX * t, y + vy * t, z + velocityZ * t};
    }

    public void write(PacketByteBuf buf) {
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeFloat(velocityX); buf.writeFloat(velocityY); buf.writeFloat(velocityZ);
        buf.writeFloat(bodyYaw); buf.writeByte(flags);
    }

    public static PartnerState read(PacketByteBuf buf) {
        return new PartnerState(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readByte());
    }
}

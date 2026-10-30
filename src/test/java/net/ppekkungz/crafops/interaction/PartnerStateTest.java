package net.ppekkungz.crafops.interaction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartnerStateTest {
    private static PartnerState state(byte flags) {
        return new PartnerState(10, 64, -5, 0.2f, 0.3f, -0.1f, 0f, flags);
    }

    @Test void extrapolatesAlongVelocity() {
        double[] p = state((byte) 0).extrapolate(3);
        assertArrayEquals(new double[]{10.6, 64.9, -5.3}, p, 1e-5);
    }

    @Test void groundedStateDoesNotSinkOrRise() {
        assertEquals(64.0, state(PartnerState.ON_GROUND).extrapolate(3)[1], 1e-9);
    }

    @Test void clampsExtrapolationWindow() {
        double[] far = state((byte) 0).extrapolate(100);
        assertEquals(10 + 0.2 * PartnerState.MAX_EXTRAPOLATION_TICKS, far[0], 1e-5);
        assertEquals(10.0, state((byte) 0).extrapolate(-5)[0], 1e-9);
    }
}

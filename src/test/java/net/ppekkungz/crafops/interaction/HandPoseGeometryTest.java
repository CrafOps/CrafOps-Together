package net.ppekkungz.crafops.interaction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandPoseGeometryTest {
    @Test void pairedArmsAreSymmetricAndLeaveConfiguredGap() {
        double spacing = 1.04, gap = .08;
        var right = HandPoseGeometry.angles(spacing, 0, gap, true);
        var left = HandPoseGeometry.angles(spacing, 0, gap, false);
        assertEquals(right.pitch(), left.pitch(), 0.00001);
        assertEquals(right.roll(), -left.roll(), 0.00001);
        assertTrue(Math.abs(right.roll()) < .5, "A natural hold should not splay the arms too far");
        assertTrue(Math.abs(right.roll()) > .05, "The hands must visibly extend toward each other");
    }

    @Test void behindOffsetTiltsBothArmsWithoutBreakingRollSymmetry() {
        var right = HandPoseGeometry.angles(1.04, .15, .08, true);
        var left = HandPoseGeometry.angles(1.04, .15, .08, false);
        assertEquals(right.roll(), -left.roll(), .01);
        assertEquals(right.pitch(), -left.pitch(), .01);
    }

    @Test void jumpingPartnerRaisesTheConnectedArm() {
        var normal = HandPoseGeometry.angles(1.04, .28, .08, true, 0.0);
        var lifted = HandPoseGeometry.angles(1.04, .28, .08, true, .4);
        assertTrue(Math.abs(lifted.roll()) > Math.abs(normal.roll()));
    }
}

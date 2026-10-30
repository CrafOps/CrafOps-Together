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

    @Test void partnerFarAboveAimsTheHandAboveTheShoulderOnTheSameSide() {
        var right = HandPoseGeometry.angles(1.04, 0, .08, true, 3.0);
        var left = HandPoseGeometry.angles(1.04, 0, .08, false, 3.0);
        assertTrue(right.roll() > Math.PI / 2, "aiming upward needs more than a sideways roll");
        assertEquals(right.roll(), -left.roll(), 1e-5);
    }

    @Test void partnerOneBlockLowerIsReachedDownTowardNotDangledAt() {
        var level = HandPoseGeometry.angles(1.04, 0, .08, false, 0.0);
        var upper = HandPoseGeometry.angles(1.04, 0, .08, false, -1.0);
        assertTrue(Math.abs(upper.roll()) > Math.abs(level.roll()), "the upper partner's arm must swing out toward the lower one");
        assertTrue(Math.abs(upper.roll()) < Math.PI / 2, "but still point downward");
    }

    @Test void upperAndLowerArmsLieOnTheSameShoulderLine() {
        var upper = HandPoseGeometry.angles(1.04, 0, .08, false, -1.0);
        var lower = HandPoseGeometry.angles(1.04, 0, .08, true, 1.0);
        // Pointing at each other along one line: the lower arm's roll is the upper arm's, flipped over.
        assertEquals(Math.PI, Math.abs(upper.roll()) + Math.abs(lower.roll()), 1e-5);
    }
}

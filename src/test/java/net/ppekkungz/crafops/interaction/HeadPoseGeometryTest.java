package net.ppekkungz.crafops.interaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeadPoseGeometryTest {
    @Test
    void usesTheShortestAngleAcrossTheWrapBoundary() {
        assertEquals(-2.0f, HeadPoseGeometry.relativeYaw(179.0f, -179.0f, 80.0f));
        assertEquals(2.0f, HeadPoseGeometry.relativeYaw(-179.0f, 179.0f, 80.0f));
    }

    @Test
    void preventsFullNeckRotation() {
        assertEquals(0.0f, HeadPoseGeometry.clampRelativeYaw(360.0f, 80.0f));
        assertEquals(80.0f, HeadPoseGeometry.clampRelativeYaw(140.0f, 80.0f));
        assertEquals(-80.0f, HeadPoseGeometry.clampRelativeYaw(-140.0f, 80.0f));
    }
}

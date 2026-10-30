package net.ppekkungz.crafops.interaction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FollowSteeringTest {
    private static final double SPRING = .35, DEADZONE = .06, SPEED = FollowSteering.WALK_SPEED;

    @Test void goalStraightAheadPressesForward() {
        // Yaw 0 faces +Z in Minecraft.
        var input = FollowSteering.steer(0, .3, 0, 0, 0f, SPRING, DEADZONE, SPEED);
        assertTrue(input.forward() > 0);
        assertEquals(0, input.sideways(), 1e-6);
    }

    @Test void goalToTheLeftPressesLeft() {
        // Facing +Z, the player's left is +X; vanilla's positive sideways input is left.
        var input = FollowSteering.steer(.3, 0, 0, 0, 0f, SPRING, DEADZONE, SPEED);
        assertTrue(input.sideways() > 0);
        assertEquals(0, input.forward(), 1e-6);
    }

    @Test void inputIsRelativeToCameraYaw() {
        // Facing -X (yaw 90), a goal at -X is straight ahead.
        var input = FollowSteering.steer(-.3, 0, 0, 0, 90f, SPRING, DEADZONE, SPEED);
        assertTrue(input.forward() > 0);
        assertEquals(0, input.sideways(), 1e-5);
    }

    @Test void restsInsideDeadzoneWhenLeaderIsStill() {
        assertEquals(FollowSteering.Input.NONE, FollowSteering.steer(.02, .02, 0, 0, 0f, SPRING, DEADZONE, SPEED));
    }

    @Test void matchesLeaderSpeedEvenWithNoError() {
        var input = FollowSteering.steer(0, 0, 0, SPEED * .5, 0f, SPRING, DEADZONE, SPEED);
        assertEquals(.5, input.forward(), 1e-4);
    }

    @Test void clampsInputButReportsExcessDemand() {
        var input = FollowSteering.steer(0, 5, 0, SPEED, 0f, SPRING, DEADZONE, SPEED);
        assertEquals(1.0, Math.hypot(input.forward(), input.sideways()), 1e-4);
        assertTrue(input.demand() > 1.0);
    }

    @Test void approachAngleTakesTheShortWayAndStopsAtTarget() {
        assertEquals(-170f + -10f, FollowSteering.approachAngle(-170f, 170f, 10f), 1e-4);
        assertEquals(30f, FollowSteering.approachAngle(25f, 30f, 10f), 1e-4);
    }
}

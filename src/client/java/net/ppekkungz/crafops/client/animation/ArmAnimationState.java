package net.ppekkungz.crafops.client.animation;

import java.util.*;

/** Independent smooth progress for each hand. */
public final class ArmAnimationState {
    private static final Map<UUID, Progress> states = new HashMap<>();
    public static void tick(UUID id, boolean leftRaised, boolean rightRaised) {
        Progress old = states.getOrDefault(id, new Progress(0, 0));
        float left = approach(old.left, leftRaised ? 1 : 0, leftRaised ? .10f : .13f);
        float right = approach(old.right, rightRaised ? 1 : 0, rightRaised ? .10f : .13f);
        if (left == 0 && right == 0) states.remove(id); else states.put(id, new Progress(left, right));
    }
    public static float left(UUID id) { return states.getOrDefault(id, Progress.ZERO).left; }
    public static float right(UUID id) { return states.getOrDefault(id, Progress.ZERO).right; }
    public static void clear() { states.clear(); }
    private static float approach(float value, float target, float step) { return value < target ? Math.min(target, value + step) : Math.max(target, value - step); }
    private record Progress(float left, float right) { static final Progress ZERO = new Progress(0, 0); }
    private ArmAnimationState() {}
}

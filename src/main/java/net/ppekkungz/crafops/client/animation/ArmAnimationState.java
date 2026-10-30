package net.ppekkungz.crafops.client.animation;

import java.util.*;

/** Independent, frame-interpolated and eased reach progress for each hand. */
public final class ArmAnimationState {
    private static final float RAISE_STEP = .09f;
    private static final float LOWER_STEP = .12f;
    private static final Map<UUID, Progress> states = new HashMap<>();

    public static void tick(UUID id, boolean leftRaised, boolean rightRaised) {
        Progress old = states.get(id);
        if (old == null) {
            if (!leftRaised && !rightRaised) return;
            old = new Progress();
            states.put(id, old);
        }
        old.prevLeft = old.left; old.prevRight = old.right;
        old.left = approach(old.left, leftRaised ? 1 : 0, leftRaised ? RAISE_STEP : LOWER_STEP);
        old.right = approach(old.right, rightRaised ? 1 : 0, rightRaised ? RAISE_STEP : LOWER_STEP);
        if (old.left == 0 && old.right == 0 && old.prevLeft == 0 && old.prevRight == 0) states.remove(id);
    }
    public static float left(UUID id, float delta) { Progress p = states.get(id); return p == null ? 0 : ease(p.prevLeft + (p.left - p.prevLeft) * delta); }
    public static float right(UUID id, float delta) { Progress p = states.get(id); return p == null ? 0 : ease(p.prevRight + (p.right - p.prevRight) * delta); }
    public static void retain(Set<UUID> present) { states.keySet().retainAll(present); }
    public static void clear() { states.clear(); }

    private static float approach(float value, float target, float step) { return value < target ? Math.min(target, value + step) : Math.max(target, value - step); }
    /** Ease-in-out so the reach starts and settles softly instead of moving at constant speed. */
    private static float ease(float t) { return t * t * (3f - 2f * t); }
    private static final class Progress { float prevLeft, left, prevRight, right; }
    private ArmAnimationState() {}
}

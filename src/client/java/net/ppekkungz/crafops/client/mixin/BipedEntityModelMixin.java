package net.ppekkungz.crafops.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.ppekkungz.crafops.client.animation.ArmAnimationState;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BipedEntityModelMixin — Smooth directional arm pose for hand-holding.
 *
 * Role is determined by isInitiator() — no dot product needed.
 * Initiator = LEADER  → right arm swept back  (leading someone behind)
 * Target    = FOLLOWER → right arm forward+up (following, arm extended)
 */
@Mixin(BipedEntityModel.class)
public class BipedEntityModelMixin<T extends LivingEntity> {

    // ── Leader pose — arm swept backward ─────────────────────────────────────
    // pitch positive = arm rotates backward
    // roll negative  = arm comes inward slightly
    private static final float LEADER_RIGHT_PITCH =  0.9f;
    private static final float LEADER_RIGHT_ROLL  = -0.2f;
    private static final float LEADER_LEFT_PITCH  =  0.05f;
    private static final float LEADER_LEFT_ROLL   =  0.05f;

    // ── Follower pose — arm reaches forward and up ────────────────────────────
    // pitch negative = arm rotates forward+up
    // roll positive  = arm spreads outward slightly
    private static final float FOLLOWER_RIGHT_PITCH = -1.2f;
    private static final float FOLLOWER_RIGHT_ROLL  =  0.3f;
    private static final float FOLLOWER_LEFT_PITCH  =  0.15f;
    private static final float FOLLOWER_LEFT_ROLL   = -0.1f;

    @Inject(
            method = "setAngles",
            at = @At("TAIL")
    )
    private void onSetAngles(T entity,
                             float limbAngle,
                             float limbDistance,
                             float animationProgress,
                             float headYaw,
                             float headPitch,
                             CallbackInfo ci) {

        if (!(entity instanceof AbstractClientPlayerEntity player)) return;

        float progress = ArmAnimationState.getProgress(player.getUuid());
        if (progress <= 0.0f) return;

        BipedEntityModel<T> self = (BipedEntityModel<T>) (Object) this;

        // Role: initiator = leader (arm back), target = follower (arm forward)
        boolean isLeader = ClientConnectionCache.isInitiator(player.getUuid());

        float targetRightPitch = isLeader ? LEADER_RIGHT_PITCH  : FOLLOWER_RIGHT_PITCH;
        float targetRightRoll  = isLeader ? LEADER_RIGHT_ROLL   : FOLLOWER_RIGHT_ROLL;
        float targetLeftPitch  = isLeader ? LEADER_LEFT_PITCH   : FOLLOWER_LEFT_PITCH;
        float targetLeftRoll   = isLeader ? LEADER_LEFT_ROLL    : FOLLOWER_LEFT_ROLL;

        self.rightArm.pitch = lerp(self.rightArm.pitch, targetRightPitch, progress);
        self.rightArm.roll  = lerp(self.rightArm.roll,  targetRightRoll,  progress);
        self.leftArm.pitch  = lerp(self.leftArm.pitch,  targetLeftPitch,  progress);
        self.leftArm.roll   = lerp(self.leftArm.roll,   targetLeftRoll,   progress);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
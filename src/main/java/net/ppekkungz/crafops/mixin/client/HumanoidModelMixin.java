package net.ppekkungz.crafops.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.ppekkungz.crafops.client.animation.ArmAnimationState;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.renderer.HandHoldVisuals;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.ppekkungz.crafops.interaction.FormationSettings;
import net.ppekkungz.crafops.interaction.HandPoseGeometry;
import net.ppekkungz.crafops.interaction.HeadPoseGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reaches the held hand toward the partner. Both clasped hands sway with the same leader's stride,
 * so they move as one instead of two arms swinging independently.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
    private static final float STRIDE_FREQUENCY = 0.6662f;
    private static final double MIN_REACH_SPACING = 0.7;
    private static final double MAX_REACH_SPACING = 1.8;
    private static final double MAX_REACH_FORWARD = 0.8;
    private static final double MAX_REACH_LIFT = 4.0;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void crafopsTogether$pose(T entity, float limbAngle, float limbDistance, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) return;
        HumanoidModel<T> model = (HumanoidModel<T>) (Object) this;
        float delta = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float left = ArmAnimationState.left(player.getUUID(), delta), right = ArmAnimationState.right(player.getUUID(), delta);
        if (left == 0 && right == 0) return;

        FormationSettings settings = ClientConnectionCache.settings();
        ClientConnectionCache.Link incoming = ClientConnectionCache.incomingLink(player.getUUID());
        ClientConnectionCache.Link outgoing = ClientConnectionCache.outgoingLink(player.getUUID());

        if (incoming != null) {
            model.head.yRot = (float) Math.toRadians(HeadPoseGeometry.clampRelativeYaw(headYaw, 80.0f));
        }

        if (left > 0) {
            HandPoseGeometry.ArmAngles pose = incoming == null
                    ? HandPoseGeometry.angles(settings.sideOffset(), settings.behindOffset(), settings.handGap(), false)
                    : reachToward(player, incoming.leader(), false, delta, settings,
                            ClientConnectionCache.behindOffset(incoming, delta), ClientConnectionCache.followerArmLift(incoming, delta));
            float sway = incoming == null ? 0 : sway(player, incoming.leader(), delta, settings);
            model.leftArm.xRot = lerp(model.leftArm.xRot, pose.pitch() + sway, left);
            model.leftArm.zRot = lerp(model.leftArm.zRot, pose.roll(), left);
            model.leftArm.yRot = lerp(model.leftArm.yRot, 0, left);
        }
        if (right > 0) {
            HandPoseGeometry.ArmAngles pose = outgoing == null
                    ? HandPoseGeometry.angles(settings.sideOffset(), settings.behindOffset(), settings.handGap(), true)
                    : reachToward(player, outgoing.follower(), true, delta, settings,
                            -ClientConnectionCache.behindOffset(outgoing, delta), ClientConnectionCache.leaderArmLift(outgoing, delta));
            float sway = outgoing == null ? 0 : sway(player, player.getUUID(), delta, settings);
            model.rightArm.xRot = lerp(model.rightArm.xRot, pose.pitch() + sway, right);
            model.rightArm.zRot = lerp(model.rightArm.zRot, pose.roll(), right);
            model.rightArm.yRot = lerp(model.rightArm.yRot, 0, right);
        }
    }

    /**
     * Aims at where the partner is actually drawn this frame (lag correction included), so the two hands
     * meet even while one partner trails a little. Falls back to the formation pose if the partner is not loaded.
     */
    private static HandPoseGeometry.ArmAngles reachToward(AbstractClientPlayer self, java.util.UUID partnerId,
                                                          boolean rightArm, float delta, FormationSettings settings,
                                                          double partnerForwardFallback, double lift) {
        double spacing = settings.sideOffset(), partnerForward = partnerForwardFallback;
        if (self.level() != null && self.level().getPlayerByUUID(partnerId) instanceof AbstractClientPlayer partner) {
            Vec3 offset = HandHoldVisuals.renderedPosition(partner, delta).subtract(HandHoldVisuals.renderedPosition(self, delta));
            double yaw = Math.toRadians(Mth.rotLerp(delta, self.yBodyRotO, self.yBodyRot));
            double toRight = offset.x * -Math.cos(yaw) + offset.z * -Math.sin(yaw);
            double forward = offset.x * -Math.sin(yaw) + offset.z * Math.cos(yaw);
            double up = offset.y;
            // Swimming/crawling tilts the whole body forward (same angle as PlayerRenderer#setupRotations),
            // so express "forward" and "up" in the tilted body's own frame before aiming the arm.
            float leaning = self.getSwimAmount(delta);
            if (leaning > 0) {
                double tilt = Math.toRadians(-Mth.lerp(leaning, 0.0f, self.isInWater() ? -90.0f - self.getViewXRot(delta) : -90.0f));
                double bodyUp = forward * Math.sin(tilt) + up * Math.cos(tilt);
                forward = forward * Math.cos(tilt) - up * Math.sin(tilt);
                up = bodyUp;
            }
            spacing = Mth.clamp(rightArm ? toRight : -toRight, MIN_REACH_SPACING, MAX_REACH_SPACING);
            partnerForward = Mth.clamp(forward, -MAX_REACH_FORWARD, MAX_REACH_FORWARD);
            // Aim up or down at the partner's real height: mid-jump, on a step, or flying above.
            lift = Mth.clamp(up, -MAX_REACH_LIFT, MAX_REACH_LIFT);
        }
        // HandPoseGeometry's "behind" is the partner's lag for the right arm and its lead for the left arm.
        return HandPoseGeometry.angles(spacing, rightArm ? -partnerForward : partnerForward, settings.handGap(), rightArm, lift);
    }

    private static float sway(AbstractClientPlayer self, java.util.UUID leaderId, float delta, FormationSettings settings) {
        if (self.isSwimming() || self.level() == null) return 0;
        LivingEntity leader = self.level().getPlayerByUUID(leaderId);
        if (leader == null) return 0;
        float speed = Math.min(1.0f, leader.walkAnimation.speed(delta));
        return (float) (Math.cos(leader.walkAnimation.position(delta) * STRIDE_FREQUENCY) * settings.armSway() * speed);
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}

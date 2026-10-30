package net.ppekkungz.crafops.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.ppekkungz.crafops.client.animation.ArmAnimationState;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.client.renderer.HandHoldVisuals;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin<T extends LivingEntity> {
    private static final float STRIDE_FREQUENCY = 0.6662f;
    private static final double MIN_REACH_SPACING = 0.7;
    private static final double MAX_REACH_SPACING = 1.8;
    private static final double MAX_REACH_FORWARD = 0.8;
    private static final double MAX_REACH_LIFT = 4.0;

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void crafopsTogether$pose(T entity, float limbAngle, float limbDistance, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;
        BipedEntityModel<T> model = (BipedEntityModel<T>) (Object) this;
        float delta = MinecraftClient.getInstance().getTickDelta();
        float left = ArmAnimationState.left(player.getUuid(), delta), right = ArmAnimationState.right(player.getUuid(), delta);
        if (left == 0 && right == 0) return;

        FormationSettings settings = ClientConnectionCache.settings();
        ClientConnectionCache.Link incoming = ClientConnectionCache.incomingLink(player.getUuid());
        ClientConnectionCache.Link outgoing = ClientConnectionCache.outgoingLink(player.getUuid());

        if (incoming != null) {
            model.head.yaw = (float) Math.toRadians(HeadPoseGeometry.clampRelativeYaw(headYaw, 80.0f));
        }

        if (left > 0) {
            HandPoseGeometry.ArmAngles pose = incoming == null
                    ? HandPoseGeometry.angles(settings.sideOffset(), settings.behindOffset(), settings.handGap(), false)
                    : reachToward(player, incoming.leader(), false, delta, settings,
                            ClientConnectionCache.behindOffset(incoming, delta), ClientConnectionCache.followerArmLift(incoming, delta));
            float sway = incoming == null ? 0 : sway(player, incoming.leader(), delta, settings);
            model.leftArm.pitch = lerp(model.leftArm.pitch, pose.pitch() + sway, left);
            model.leftArm.roll = lerp(model.leftArm.roll, pose.roll(), left);
            model.leftArm.yaw = lerp(model.leftArm.yaw, 0, left);
        }
        if (right > 0) {
            HandPoseGeometry.ArmAngles pose = outgoing == null
                    ? HandPoseGeometry.angles(settings.sideOffset(), settings.behindOffset(), settings.handGap(), true)
                    : reachToward(player, outgoing.follower(), true, delta, settings,
                            -ClientConnectionCache.behindOffset(outgoing, delta), ClientConnectionCache.leaderArmLift(outgoing, delta));
            float sway = outgoing == null ? 0 : sway(player, player.getUuid(), delta, settings);
            model.rightArm.pitch = lerp(model.rightArm.pitch, pose.pitch() + sway, right);
            model.rightArm.roll = lerp(model.rightArm.roll, pose.roll(), right);
            model.rightArm.yaw = lerp(model.rightArm.yaw, 0, right);
        }
    }

    /**
     * Aims at where the partner is actually drawn this frame (lag correction included), so the two hands
     * meet even while one partner trails a little. Falls back to the formation pose if the partner is not loaded.
     */
    private static HandPoseGeometry.ArmAngles reachToward(AbstractClientPlayerEntity self, java.util.UUID partnerId,
                                                          boolean rightArm, float delta, FormationSettings settings,
                                                          double partnerForwardFallback, double lift) {
        double spacing = settings.sideOffset(), partnerForward = partnerForwardFallback;
        if (self.getWorld() != null && self.getWorld().getPlayerByUuid(partnerId) instanceof AbstractClientPlayerEntity partner) {
            Vec3d offset = HandHoldVisuals.renderedPosition(partner, delta).subtract(HandHoldVisuals.renderedPosition(self, delta));
            double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(delta, self.prevBodyYaw, self.bodyYaw));
            double toRight = offset.x * -Math.cos(yaw) + offset.z * -Math.sin(yaw);
            double forward = offset.x * -Math.sin(yaw) + offset.z * Math.cos(yaw);
            double up = offset.y;
            // Swimming/crawling tilts the whole body forward (same angle as PlayerEntityRenderer#setupTransforms),
            // so express "forward" and "up" in the tilted body's own frame before aiming the arm.
            float leaning = self.getLeaningPitch(delta);
            if (leaning > 0) {
                double tilt = Math.toRadians(-MathHelper.lerp(leaning, 0.0f, self.isTouchingWater() ? -90.0f - self.getPitch(delta) : -90.0f));
                double bodyUp = forward * Math.sin(tilt) + up * Math.cos(tilt);
                forward = forward * Math.cos(tilt) - up * Math.sin(tilt);
                up = bodyUp;
            }
            spacing = MathHelper.clamp(rightArm ? toRight : -toRight, MIN_REACH_SPACING, MAX_REACH_SPACING);
            partnerForward = MathHelper.clamp(forward, -MAX_REACH_FORWARD, MAX_REACH_FORWARD);
            // Aim up or down at the partner's real height: mid-jump, on a step, or flying above.
            lift = MathHelper.clamp(up, -MAX_REACH_LIFT, MAX_REACH_LIFT);
        }
        // HandPoseGeometry's "behind" is the partner's lag for the right arm and its lead for the left arm.
        return HandPoseGeometry.angles(spacing, rightArm ? -partnerForward : partnerForward, settings.handGap(), rightArm, lift);
    }

    private static float sway(AbstractClientPlayerEntity self, java.util.UUID leaderId, float delta, FormationSettings settings) {
        if (self.isSwimming() || self.getWorld() == null) return 0;
        LivingEntity leader = self.getWorld().getPlayerByUuid(leaderId);
        if (leader == null) return 0;
        float speed = Math.min(1.0f, leader.limbAnimator.getSpeed(delta));
        return (float) (Math.cos(leader.limbAnimator.getPos(delta) * STRIDE_FREQUENCY) * settings.armSway() * speed);
    }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}

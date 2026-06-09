package net.ppekkungz.crafops.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PlayerEntityRendererMixin — Injects arm raise pose for hand-holding players.
 *
 * Hook: setModelPose() — called every frame before the model is rendered.
 * This is the correct place to override limb angles because it runs
 * after vanilla sets up the base pose but before the model is drawn.
 *
 * Arm angle convention (Minecraft model coordinates):
 *   rightArm.pitch = negative value = arm raises forward/up
 *   rightArm.roll  = positive value = arm swings outward
 *
 * Target pose: arm raised ~45° forward and ~30° outward
 * mimicking holding someone's hand at side height.
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    /**
     * Inject AFTER vanilla setModelPose so we override its arm settings,
     * not the other way around.
     */
    @Inject(
            method = "setModelPose",
            at = @At("TAIL")
    )
    private void onSetModelPose(AbstractClientPlayerEntity player,
                                CallbackInfo ci) {

        // Only modify pose if this player is in a hand-hold connection
        if (!ClientConnectionCache.isConnected(player.getUuid())) return;

        // Get the renderer's model via the accessor pattern
        // PlayerEntityRenderer extends LivingEntityRenderer<PlayerEntity, PlayerEntityModel>
        PlayerEntityRenderer self = (PlayerEntityRenderer) (Object) this;
        PlayerEntityModel<AbstractClientPlayerEntity> model =
                (PlayerEntityModel<AbstractClientPlayerEntity>) self.getModel();

        // ── Raise right arm ───────────────────────────────────────────────────
        // pitch: negative = forward raise. -1.2 rad ≈ 68° upward
        // roll:  positive = outward from body. 0.3 rad ≈ 17° outward
        model.rightArm.pitch = -1.2f;
        model.rightArm.roll  =  0.3f;

        // Slight left arm swing back for natural counterbalance
        model.leftArm.pitch  =  0.2f;
        model.leftArm.roll   = -0.1f;
    }
}
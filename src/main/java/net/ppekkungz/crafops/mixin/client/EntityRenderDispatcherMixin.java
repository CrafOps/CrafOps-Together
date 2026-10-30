package net.ppekkungz.crafops.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.ppekkungz.crafops.client.renderer.HandHoldVisuals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Moves a holding player's whole render (model, shadow, fire, name tag) to where the pair is drawn hand in hand.
 * Shifting only the model leaves the shadow behind at the real position, which makes the led player look afloat.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    private static final String RENDER = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double crafopsTogether$shiftX(double x, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true, ordinal = 1) float partialTick) {
        return x + correction(entity, partialTick).x;
    }

    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double crafopsTogether$shiftY(double y, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true, ordinal = 1) float partialTick) {
        return y + correction(entity, partialTick).y;
    }

    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double crafopsTogether$shiftZ(double z, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true, ordinal = 1) float partialTick) {
        return z + correction(entity, partialTick).z;
    }

    private static Vec3 correction(Entity entity, float partialTick) {
        return entity instanceof AbstractClientPlayer player ? HandHoldVisuals.renderCorrection(player, partialTick) : Vec3.ZERO;
    }
}

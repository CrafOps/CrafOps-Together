package net.ppekkungz.crafops.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors adjacent-player collision suppression for client prediction. */
@Mixin(Entity.class)
public abstract class ClientEntityCollisionMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void crafopsTogether$cancelConnectedPush(Entity other, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide() && self instanceof Player && other instanceof Player
                && ClientConnectionCache.isAdjacent(self.getUUID(), other.getUUID())) ci.cancel();
    }
}

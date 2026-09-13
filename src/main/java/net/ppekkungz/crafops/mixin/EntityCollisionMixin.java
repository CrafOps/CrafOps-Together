package net.ppekkungz.crafops.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.ppekkungz.crafops.interaction.InteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents connected players from pushing each other when standing close together.
 * Intercepts Entity.pushAwayFrom() and cancels it if both entities are players
 * connected via InteractionManager.
 */
@Mixin(Entity.class)
public abstract class EntityCollisionMixin {

    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void onPushAwayFrom(Entity entity, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.getWorld().isClient()) return;

        // Only care about Player vs Player collision
        if (self instanceof PlayerEntity && entity instanceof PlayerEntity) {
            InteractionManager manager = InteractionManager.getInstance();
            if (manager.isAdjacent(self.getUuid(), entity.getUuid())) ci.cancel();
        }
    }
}

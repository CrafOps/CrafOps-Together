package net.ppekkungz.crafops.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.ppekkungz.crafops.interaction.InteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Turns an empty-main-hand player interaction into a validated hand-hold action. */
@Mixin(PlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void crafopsTogether$interact(Entity entity, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        PlayerEntity raw = (PlayerEntity) (Object) this;
        if (raw.getWorld().isClient() || hand != Hand.MAIN_HAND || !raw.getMainHandStack().isEmpty()) return;
        if (!(raw instanceof ServerPlayerEntity self) || !(entity instanceof ServerPlayerEntity target)) return;
        InteractionManager manager = InteractionManager.getInstance();
        boolean handled;
        if (manager.isAdjacent(self.getUuid(), target.getUuid())) handled = manager.disconnectLink(self, target.getUuid());
        else if (self.isSneaking() && manager.isConnected(self.getUuid())) handled = manager.disconnectPlayer(self);
        else handled = manager.requestConnection(self, target);
        if (handled) cir.setReturnValue(ActionResult.SUCCESS);
    }
}

package net.ppekkungz.crafops.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.ppekkungz.crafops.interaction.InteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ServerPlayerEntityMixin — Intercepts player-on-entity right-click.
 *
 * Target method: PlayerEntity.interact(Entity, Hand)
 * Found in PlayerEntity source — handles all player→entity interactions.
 *
 * Flow in vanilla (from PlayerEntity.interact):
 *   1. Spectator check → open screen if NamedScreenHandlerFactory
 *   2. entity.interact(player, hand) — entity-driven interaction
 *   3. itemStack.useOnEntity(...) — item-driven interaction
 *
 * We inject at HEAD before vanilla runs any of the above.
 */
@Mixin(PlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(
            method = "interact",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onInteract(Entity entity,
                            Hand hand,
                            CallbackInfoReturnable<ActionResult> cir) {

        PlayerEntity selfAsPlayer = (PlayerEntity) (Object) this;

        // Only run server-side — PlayerEntity.interact() is called on both sides
        if (selfAsPlayer.getWorld().isClient()) return;

        // Only ServerPlayerEntity should trigger our logic
        if (!(selfAsPlayer instanceof ServerPlayerEntity self)) return;

        // Main hand only
        if (hand != Hand.MAIN_HAND) return;

        // Target must be another ServerPlayerEntity
        if (!(entity instanceof ServerPlayerEntity target)) return;

        // Empty hand only — getMainHandStack() is defined in PlayerEntity
        if (!self.getMainHandStack().isEmpty()) return;

        InteractionManager manager = InteractionManager.getInstance();

        // ── Sneak + right-click → disconnect ──────────────────────────────────
        if (self.isSneaking()) {
            if (manager.isConnected(self.getUuid())) {
                manager.disconnect(self, true);
                cir.setReturnValue(ActionResult.SUCCESS);
            }
            return;
        }

        // ── Already connected → toggle off ────────────────────────────────────
        if (manager.isConnected(self.getUuid())) {
            manager.disconnect(self, true);
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }

        // ── Target already in a connection → pass through ─────────────────────
        if (manager.isConnected(target.getUuid())) {
            return;
        }

        // ── Connect ───────────────────────────────────────────────────────────
        boolean success = manager.connect(self, target);
        if (success) {
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}
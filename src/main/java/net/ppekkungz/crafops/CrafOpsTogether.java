package net.ppekkungz.crafops;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.ppekkungz.crafops.config.TogetherConfig;
import net.ppekkungz.crafops.interaction.InteractionManager;
import net.ppekkungz.crafops.network.TogetherPackets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint, loaded on both client and server.
 *
 * Responsibilities:
 *   1. Register network payloads
 *   2. Hook InteractionManager into the server tick loop
 *   3. Turn an empty-hand right-click on a player into a hand-hold action
 *   4. Register /crafops together commands
 *   5. Clean up all connections when players leave or the server stops
 */
@Mod(CrafOpsTogether.MOD_ID)
public final class CrafOpsTogether {
    public static final String MOD_ID = "crafops_together";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public CrafOpsTogether(IEventBus modBus) {
        TogetherConfig.load();
        modBus.addListener(TogetherPackets::register);

        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener((ServerTickEvent.Post event) -> {
            InteractionManager.getInstance().onServerTick(event.getServer());
            TogetherDebug.tick(event.getServer());
            TogetherPackets.tick(event.getServer());
        });
        game.addListener((RegisterCommandsEvent event) -> TogetherCommands.register(event.getDispatcher()));
        game.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) TogetherPackets.sendSnapshot(player);
        });
        game.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) InteractionManager.getInstance().handlePlayerLeaving(player);
        });
        game.addListener((ServerStoppingEvent event) -> {
            LOGGER.info("[CrafOps: Together] Server stopping — clearing all connections.");
            InteractionManager.getInstance().disconnectAll(event.getServer());
        });
        game.addListener(CrafOpsTogether::onInteractPlayer);
    }

    /** An empty main hand on another player: reach out, let go of a partner, or (sneaking) let go of everyone. */
    private static void onInteractPlayer(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) return;
        if (!(event.getEntity() instanceof ServerPlayer self) || !(event.getTarget() instanceof ServerPlayer target)) return;
        InteractionManager manager = InteractionManager.getInstance();
        boolean handled;
        if (manager.isAdjacent(self.getUUID(), target.getUUID())) handled = manager.disconnectLink(self, target.getUUID());
        else if (self.isShiftKeyDown() && manager.isConnected(self.getUUID())) handled = manager.disconnectPlayer(self);
        else handled = manager.requestConnection(self, target);
        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}

package net.ppekkungz.crafops.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import net.ppekkungz.crafops.config.TogetherConfig;
import org.joml.Matrix4f;

import java.util.List;
import java.util.UUID;

/**
 * HandConnectionRenderer — Draws a subtle visual line between hand-holding players.
 *
 * Registered via WorldRenderEvents.AFTER_TRANSLUCENT in CrafOpsUtilsClient.
 *
 * Design goals:
 *   - Lightweight: single GL line strip per connection
 *   - Immersive: soft color, partial transparency, no ropes/chains
 *   - Correct depth: renders after translucent pass so it blends naturally
 *   - Camera-relative: all positions offset by camera pos to avoid float precision loss
 *
 * Rendering approach:
 *   - Use Tessellator + BufferBuilder with LINES draw mode
 *   - Line drawn from initiator's right hand position → target's left hand position
 *   - Hand positions approximated from entity body position + arm offset
 *   - No custom shaders required — uses RenderSystem.setShader(GameRenderer::getPositionColorProgram)
 */
public class HandConnectionRenderer {

    // ── Visual constants ──────────────────────────────────────────────────────

    /** RGBA color of the connection line. Soft warm white, semi-transparent. */
    private static final float LINE_R = 1.0f;
    private static final float LINE_G = 0.85f;
    private static final float LINE_B = 0.75f;
    private static final float LINE_A = 0.45f;

    /**
     * Width of the rendered line in pixels.
     * Values above 2.0 may look inconsistent on some GPU drivers.
     */
    private static final float LINE_WIDTH = 1.5f;

    /**
     * Vertical offset from entity feet to approximate hand position.
     * Players are 1.8 blocks tall; hands are roughly at 60% height.
     */
    private static final double HAND_HEIGHT_OFFSET = 1.1;

    /**
     * Horizontal offset to place the line at the side of the player
     * rather than dead center, approximating arm position.
     * Applied perpendicular to the player's facing direction.
     */
    private static final double ARM_SIDE_OFFSET = 0.25;

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Main render callback. Called once per frame after the translucent pass.
     *
     * @param context WorldRenderContext provided by Fabric's WorldRenderEvents
     */
    public static void render(WorldRenderContext context) {
        // Feature toggle — skip if disabled in config
        if (!TogetherConfig.enableVisualConnection) return;

        // Get all unique connection pairs from client cache
        List<UUID[]> pairs = ClientConnectionCache.getAllConnectionPairs();
        if (pairs.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        // Camera position — all world-space positions must be offset by this
        // to keep rendering in camera-relative space (avoids float imprecision)
        Vec3d camPos = context.camera().getPos();

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        // Set up GL state for line rendering
        setupRenderState();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        matrices.push();

        for (UUID[] pair : pairs) {
            UUID uuidA = pair[0]; // initiator = LEADER
            UUID uuidB = pair[1]; // target    = FOLLOWER

            AbstractClientPlayerEntity playerA = findPlayer(client, uuidA);
            AbstractClientPlayerEntity playerB = findPlayer(client, uuidB);

            if (playerA == null || playerB == null) continue;
            if (!playerA.isAlive() || !playerB.isAlive()) continue;

            // uuidA = initiator → LEADER  (arm swept back)
            // uuidB = target    → FOLLOWER (arm reaches forward)
            Vec3d posA = getHandPosition(playerA, context.tickDelta(), camPos, true);
            Vec3d posB = getHandPosition(playerB, context.tickDelta(), camPos, false);

            drawLine(buffer, matrices, posA, posB);
        }

        tessellator.draw();

        matrices.pop();

        // Restore GL state
        restoreRenderState();
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    /**
     * Get hand position matching the directional arm pose.
     *
     * Leader  : arm swept back  → hand position is behind the player
     * Follower: arm forward+up  → hand position is in front of the player
     */
    private static Vec3d getHandPosition(AbstractClientPlayerEntity player,
                                         float tickDelta,
                                         Vec3d camPos,
                                         boolean isLeader) {
        double baseX = lerp(player.prevX, player.getX(), tickDelta);
        double baseY = lerp(player.prevY, player.getY(), tickDelta);
        double baseZ = lerp(player.prevZ, player.getZ(), tickDelta);

        float bodyYawRad = (float) Math.toRadians(
                lerpAngle(player.prevBodyYaw, player.bodyYaw, tickDelta)
        );

        double fwdX = -Math.sin(bodyYawRad);
        double fwdZ =  Math.cos(bodyYawRad);
        double rightX =  Math.cos(bodyYawRad);
        double rightZ =  Math.sin(bodyYawRad);

        // ── Match hand position to arm pose ───────────────────────────────────────
        // LEADER  pitch=+0.9 → arm back  → hand behind player
        // FOLLOWER pitch=-1.2 → arm fwd  → hand in front of player
        double handFwdOffset  = isLeader ? -0.45 :  0.5;
        double handSideOffset = isLeader ?  0.2  :  0.2;
        double handY          = isLeader ?  1.05 :  1.2;

        double handX = baseX + fwdX * handFwdOffset + rightX * handSideOffset;
        double handZ = baseZ + fwdZ * handFwdOffset + rightZ * handSideOffset;

        return new Vec3d(
                handX - camPos.x,
                baseY + handY - camPos.y,
                handZ - camPos.z
        );
    }

    /**
     * Emit two vertices (start + end) for one connection line into the buffer.
     *
     * Uses DEBUG_LINES draw mode which treats each pair of vertices
     * as an independent line segment — exactly what we need.
     */
    private static void drawLine(BufferBuilder buffer,
                                 MatrixStack matrices,
                                 Vec3d start,
                                 Vec3d end) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // Start vertex
        buffer.vertex(matrix,
                        (float) start.x,
                        (float) start.y,
                        (float) start.z)
                .color(LINE_R, LINE_G, LINE_B, LINE_A)
                .next();

        // End vertex
        buffer.vertex(matrix,
                        (float) end.x,
                        (float) end.y,
                        (float) end.z)
                .color(LINE_R, LINE_G, LINE_B, LINE_A)
                .next();
    }

    // ── GL State Management ───────────────────────────────────────────────────

    /**
     * Set up OpenGL render state for transparent line rendering.
     *
     * Changes made here MUST be reversed in restoreRenderState().
     */
    private static void setupRenderState() {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        // Enable alpha blending for transparency
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Disable depth write so the line doesn't occlude world geometry
        // but IS occluded by geometry in front of it
        RenderSystem.depthMask(false);

        // Set line width
        RenderSystem.lineWidth(LINE_WIDTH);

        // Disable face culling — lines don't have faces
        RenderSystem.disableCull();
    }

    /**
     * Restore OpenGL state to Minecraft's defaults after rendering.
     */
    private static void restoreRenderState() {
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Find a player in the client world by UUID.
     * Returns null if the player isn't currently loaded.
     */
    private static AbstractClientPlayerEntity findPlayer(MinecraftClient client, UUID uuid) {
        if (client.world == null) return null;

        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player.getUuid().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    /** Linear interpolation between two doubles. */
    private static double lerp(double prev, double current, float delta) {
        return prev + (current - prev) * delta;
    }

    /**
     * Lerp between two angles in degrees, handling 360→0 wraparound.
     * Prevents the line from spinning wildly when a player crosses 0°/360°.
     */
    private static float lerpAngle(float prevAngle, float currentAngle, float delta) {
        float diff = currentAngle - prevAngle;
        // Wrap difference to [-180, 180]
        while (diff > 180f)  diff -= 360f;
        while (diff < -180f) diff += 360f;
        return prevAngle + diff * delta;
    }

    // ── Prevent instantiation ─────────────────────────────────────────────────

    private HandConnectionRenderer() {}
}
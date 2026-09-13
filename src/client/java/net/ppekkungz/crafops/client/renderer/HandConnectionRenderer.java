package net.ppekkungz.crafops.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.ppekkungz.crafops.client.cache.ClientConnectionCache;
import org.joml.Matrix4f;
import java.util.UUID;

/** Lightweight interpolated hand-to-hand visual for every visible directed link. */
public final class HandConnectionRenderer {
    public static void render(WorldRenderContext context) {
        if (!ClientConnectionCache.visualsEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || context.matrixStack() == null) return;
        Vec3d camera = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.lineWidth(1.5f);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (ClientConnectionCache.Link link : ClientConnectionCache.getLinks()) {
            AbstractClientPlayerEntity leader = find(client, link.leader()), follower = find(client, link.follower());
            if (leader == null || follower == null || !leader.isAlive() || !follower.isAlive()) continue;
            if (leader.squaredDistanceTo(follower) > ClientConnectionCache.renderDistance() * ClientConnectionCache.renderDistance()) continue;
            Vec3d a = hand(leader, context.tickDelta(), camera, true);
            Vec3d b = hand(follower, context.tickDelta(), camera, false);
            vertex(buffer, matrix, a); vertex(buffer, matrix, b);
        }
        Tessellator.getInstance().draw();
        RenderSystem.lineWidth(1); RenderSystem.enableCull(); RenderSystem.depthMask(true); RenderSystem.disableBlend();
    }
    private static Vec3d hand(AbstractClientPlayerEntity p, float delta, Vec3d camera, boolean right) {
        double x = lerp(p.prevX, p.getX(), delta), y = lerp(p.prevY, p.getY(), delta), z = lerp(p.prevZ, p.getZ(), delta);
        float yaw = (float) Math.toRadians(lerpAngle(p.prevBodyYaw, p.bodyYaw, delta));
        double rightX = -Math.cos(yaw), rightZ = -Math.sin(yaw);
        // Shoulder (0.3125) + horizontal reach of a 0.75-block arm at roll 0.38 (~0.278).
        double side = right ? .5905 : -.5905;
        return new Vec3d(x + rightX * side - camera.x, y + .82 - camera.y, z + rightZ * side - camera.z);
    }
    private static void vertex(BufferBuilder b, Matrix4f m, Vec3d p) { b.vertex(m, (float)p.x, (float)p.y, (float)p.z).color(1f, .82f, .76f, .5f).next(); }
    private static AbstractClientPlayerEntity find(MinecraftClient c, UUID id) { for (AbstractClientPlayerEntity p : c.world.getPlayers()) if (p.getUuid().equals(id)) return p; return null; }
    private static double lerp(double a, double b, float t) { return a + (b - a) * t; }
    private static float lerpAngle(float a, float b, float t) { float d = (b - a) % 360f; if (d > 180) d -= 360; if (d < -180) d += 360; return a + d * t; }
    private HandConnectionRenderer() {}
}

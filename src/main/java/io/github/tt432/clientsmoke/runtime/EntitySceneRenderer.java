package io.github.tt432.clientsmoke.runtime;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

/**
 * Renders Minecraft entities into a {@link RenderTarget} (FBO) with controlled
 * orthographic projection, inventory-style lighting, and a 30&deg; downward viewing angle.
 *
 * <p>Designed for screenshot-based smoke tests. Usage in a smoke test constructor:</p>
 * <pre>{@code
 * ClientSmokeVisualHooks.setScene(512, 512, (mc, rt) -> {
 *     EntitySceneRenderer.beginScene(mc, rt, 0xFF1A1A2E);
 *     EntitySceneRenderer.renderEntityAt(mc, spider,   128, 128, 20, 45);
 *     EntitySceneRenderer.renderEntityAt(mc, slime,    384, 128, 20, -30);
 *     EntitySceneRenderer.renderEntityAt(mc, sheep,    128, 384, 15, 210);
 *     EntitySceneRenderer.renderEntityAt(mc, creeper,  384, 384, 20, 0);
 *     EntitySceneRenderer.endScene(mc);
 * }, null);
 * }</pre>
 *
 * @author TT432
 */
public final class EntitySceneRenderer {

    /** Standard inventory view pitch (degrees). */
    private static final float VIEW_PITCH_DEG = 30.0F;

    /** Z depth for entity placement within ortho near/far range [1000, 21000]. */
    private static final float ENTITY_Z = -11000.0F;

    private EntitySceneRenderer() {}

    // ── Public API ────────────────────────────────────────────────

    /**
     * Renders a single entity centered in the given RenderTarget.
     * Clears the FBO with the specified background color before rendering.
     *
     * @param mc      the Minecraft instance
     * @param rt      the render target to render into
     * @param entity  the entity to render (created via {@code EntityType.create(level)})
     * @param scale   scale factor (higher = larger entity)
     * @param yaw     entity yaw rotation in degrees
     * @param bgArgb  ARGB background clear color
     */
    public static void renderEntity(Minecraft mc, RenderTarget rt, Entity entity,
                                    float scale, float yaw, int bgArgb) {
        beginScene(mc, rt, bgArgb);
        renderEntityAt(mc, entity, rt.width / 2.0F, rt.height / 2.0F, scale, yaw);
        endScene(mc);
    }

    /**
     * Binds the given RenderTarget, clears it, sets up orthographic projection
     * and inventory lighting. Call before one or more {@link #renderEntityAt} calls.
     *
     * @param mc      the Minecraft instance
     * @param rt      the render target to prepare
     * @param bgArgb  ARGB background clear color
     */
    public static void beginScene(Minecraft mc, RenderTarget rt, int bgArgb) {
        rt.bindWrite(true);

        float a = ((bgArgb >> 24) & 0xFF) / 255.0F;
        float r = ((bgArgb >> 16) & 0xFF) / 255.0F;
        float g = ((bgArgb >> 8) & 0xFF) / 255.0F;
        float b = (bgArgb & 0xFF) / 255.0F;
        RenderSystem.clearColor(r, g, b, a);
        RenderSystem.clearDepth(1.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        Matrix4f proj = new Matrix4f().setOrtho(
                0, rt.width,
                rt.height, 0,
                1000.0F, 21000.0F);
        //? if <26.1 {
        RenderSystem.setProjectionMatrix(proj, VertexSorting.ORTHOGRAPHIC_Z);
        //?}

        Lighting.setupForEntityInInventory();
    }

    /**
     * Renders a single entity at a specific position within the currently bound FBO.
     * The entity is drawn with a 30&deg; downward viewing angle.
     *
     * <p>Must be called between {@link #beginScene} and {@link #endScene}.</p>
     *
     * @param mc      the Minecraft instance
     * @param entity  the entity to render
     * @param centerX center X in FBO pixel coordinates
     * @param centerY center Y in FBO pixel coordinates
     * @param scale   scale factor
     * @param yaw     entity yaw rotation in degrees
     */
    public static void renderEntityAt(Minecraft mc, Entity entity,
                                      float centerX, float centerY,
                                      float scale, float yaw) {
        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, ENTITY_Z);
        poseStack.scale(scale, scale, scale);

        // Standard inventory view: flip Y (Z-axis rotation) + pitch down
        Quaternionf viewRot = new Quaternionf().rotationZ((float) Math.PI);
        Quaternionf pitchRot = new Quaternionf().rotationX(
                (float) Math.toRadians(VIEW_PITCH_DEG));
        viewRot.mul(pitchRot);
        poseStack.mulPose(viewRot);

        setEntityOrientation(entity, yaw);

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        dispatcher.render(entity, 0, 0, 0, yaw, mc.getPartialTick(),
                poseStack, bufferSource, LightTexture.FULL_BRIGHT);

        poseStack.popPose();
    }

    /**
     * Flushes all pending render buffers and restores the shadow rendering state.
     *
     * <p>Must be called after the last {@link #renderEntityAt} call.</p>
     *
     * @param mc the Minecraft instance
     */
    public static void endScene(Minecraft mc) {
        mc.renderBuffers().bufferSource().endBatch();
        mc.getEntityRenderDispatcher().setRenderShadow(true);
    }

    // ── Internal ──────────────────────────────────────────────────

    private static void setEntityOrientation(Entity entity, float yaw) {
        entity.setYRot(yaw);
        entity.xRotO = 0;
        entity.setXRot(0);

        if (entity instanceof LivingEntity le) {
            le.yBodyRot = yaw;
            le.yBodyRotO = yaw;
            le.setYHeadRot(yaw);
            le.yHeadRotO = yaw;
        }
    }
}

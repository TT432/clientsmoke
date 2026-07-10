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
        mc.renderBuffers().bufferSource().endBatch();

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

        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().setIdentity();
        RenderSystem.applyModelViewMatrix();

        // Lighting will be set up per-entity in renderEntityAt() after the
        // model-view matrix includes our view rotation, because setupLevel(matrix)
        // transforms light directions by the inverse model-view to match
        // the shader's normal space.

        // Disable world fog: entity shader computes fog_distance as
        // length((ModelViewMat * pos).xyz), which is ~11000 at our ENTITY_Z.
        // The world's fog parameters (FogEnd ~192) would fully fog all entities.
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);

        // Suppress eyelib's RenderLivingEventAdapter during scene rendering
        // to prevent it from interfering with vanilla entity rendering in our FBO.
        ClientSmokeVisualHooks.setSuppressRenderEvents(true);
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
        setEntityOrientation(entity, yaw);

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);

        // InventoryScreen approach: put position/scale/rotation on the model-view
        // stack (which the shader uses directly), then pass a fresh identity PoseStack
        // to the entity renderer. The entity renderer adds its own transforms
        // (scale(-1,-1,1), translate(0,-1.5,0)) on the PoseStack.
        PoseStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushPose();
        mvStack.translate(centerX, centerY, ENTITY_Z);
        mvStack.scale(scale, scale, scale);
        Quaternionf viewRot = new Quaternionf().rotationZ((float) Math.PI);
        Quaternionf pitchRot = new Quaternionf().rotationX(
                (float) Math.toRadians(VIEW_PITCH_DEG));
        viewRot.mul(pitchRot);
        mvStack.mulPose(viewRot);
        RenderSystem.applyModelViewMatrix();

        // Now that the model-view matrix includes our position/scale/rotation,
        // set up level lighting. setupLevel transforms the world-space light
        // directions by the inverse model-view matrix so they match the
        // shader's ProjMat*ModelViewMat*Normal normal space.
        Lighting.setupLevel(RenderSystem.getModelViewMatrix());
        PoseStack entityPose = new PoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        // Bypass EntityRenderDispatcher to avoid Forge's RenderLivingEvent.Pre
        // and dispatcher's getRenderOffset/shadow/hitbox logic.
        net.minecraft.client.renderer.entity.EntityRenderer<? super Entity> renderer =
                dispatcher.getRenderer(entity);
        entityPose.pushPose();
        renderer.render(entity, yaw, mc.getPartialTick(), entityPose, bufferSource, LightTexture.FULL_BRIGHT);
        entityPose.popPose();
        bufferSource.endBatch();

        mvStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    /**
     * Flushes all pending render buffers and restores the shadow rendering state.
     *
     * <p>Must be called after the last {@link #renderEntityAt} call.</p>
     *
     * @param mc the Minecraft instance
     */
    public static void endScene(Minecraft mc) {
        mc.getEntityRenderDispatcher().setRenderShadow(true);

        // Restore the model-view stack saved in beginScene
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();

        // Re-enable eyelib's RenderLivingEventAdapter
        ClientSmokeVisualHooks.setSuppressRenderEvents(false);
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

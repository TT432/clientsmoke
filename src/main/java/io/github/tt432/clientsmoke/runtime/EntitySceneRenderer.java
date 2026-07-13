package io.github.tt432.clientsmoke.runtime;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
//? if <26.1 {
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
//?} else {
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.GpuDevice;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4fStack;
//?}

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

    //? if >=26.1 {
    /** Reusable ortho projection (26.1.2 声明式 uniform 缓冲模型). */
    private static final Projection PROJECTION = new Projection();
    private static final ProjectionMatrixBuffer PROJECTION_BUFFER = new ProjectionMatrixBuffer("clientsmoke-scene");
    //?}

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

        //? if <26.1 {
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
        RenderSystem.setProjectionMatrix(proj, VertexSorting.ORTHOGRAPHIC_Z);

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
        // Disable world fog and set up dispatcher context.
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);

        // LevelRenderer calls entityRenderDispatcher.prepare() before entity rendering.
        // Some renderers depend on the dispatcher's camera/cameraOrientation being set.
        mc.getEntityRenderDispatcher().prepare(mc.level, mc.gameRenderer.getMainCamera(), null);
        //?} else {
        // 26.1.2: 输出重定向到 RenderTarget 的颜色/深度纹理视图（取代 bindWrite）。
        RenderSystem.outputColorTextureOverride = rt.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = rt.getDepthTextureView();

        // 声明式清屏：CommandEncoder.clearColorAndDepthTextures（bgArgb 即 ARGB 清屏色）。
        GpuDevice device = RenderSystem.getDevice();
        device.createCommandEncoder().clearColorAndDepthTextures(
                rt.getColorTexture(), bgArgb, rt.getDepthTexture(), 1.0);

        // 投影写入 ProjectionMatrixBuffer UBO；setupOrtho(zNear, zFar, w, h, invertY=true)
        // 生成 setOrtho(0, w, h, 0, 1000, 21000)，与 <26.1 的投影矩阵一致。
        PROJECTION.setupOrtho(1000.0F, 21000.0F, rt.width, rt.height, true);
        RenderSystem.setProjectionMatrix(PROJECTION_BUFFER.getBuffer(PROJECTION), ProjectionType.ORTHOGRAPHIC);

        // 26.1.2 的 modelViewStack 是 joml Matrix4fStack；getModelViewMatrix() 直接读取栈顶，无需 apply。
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.identity();

        // 关闭世界雾（26.1.2 雾为 GpuBufferSlice；传 null 即禁用）。
        RenderSystem.setShaderFog(null);

        // dispatcher.prepare(Camera, Entity) 在 26.1.2 为两参数。
        mc.getEntityRenderDispatcher().prepare(mc.gameRenderer.getMainCamera(), null);
        //?}
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

        //? if <26.1 {
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
        // Set up level lighting with ONLY the rotation part. setupLevelDiffuseLighting
        // does matrix.transform(Vector4f(lightDir, 1.0F)) — if the matrix contains
        // translation/scale, the light "direction" becomes a nonsensical position.
        // Using a pure rotation matrix ensures lights are correctly transformed.
        org.joml.Matrix4f rotOnly = new org.joml.Matrix4f().rotation(viewRot);
        Lighting.setupLevel(rotOnly);

        // Use dispatcher.render() — same path as LevelRenderer.renderEntity().
        // This fires RenderLivingEvent.Pre, allowing eyelib's RenderLivingEventAdapter
        // to render entities with a&s definitions via eyelib's own pipeline.
        // For entities without a&s definitions, vanilla rendering proceeds.
        PoseStack entityPose = new PoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        dispatcher.render(entity, 0, 0, 0, yaw, mc.getPartialTick(),
                entityPose, bufferSource, LightTexture.FULL_BRIGHT);
        bufferSource.endBatch();
        mvStack.popPose();
        RenderSystem.applyModelViewMatrix();
        //?} else {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        // 与 <26.1 相同的 InventoryScreen 思路：位置/缩放/旋转放在 model-view 栈上
        // （RenderType.draw 经 DynamicTransforms 读取 getModelViewMatrix()），
        // 传入空 PoseStack 给实体渲染器。26.1.2 用 Matrix4fStack（无 applyModelViewMatrix）。
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();
        mvStack.translate(centerX, centerY, ENTITY_Z);
        mvStack.scale(scale, scale, scale);
        Quaternionf viewRot = new Quaternionf().rotationZ((float) Math.PI);
        Quaternionf pitchRot = new Quaternionf().rotationX(
                (float) Math.toRadians(VIEW_PITCH_DEG));
        viewRot.mul(pitchRot);
        mvStack.rotate(viewRot);

        // 26.1.2 光照为声明式 UBO；ENTITY_IN_UI 是 GUI/离屏实体渲染的标准配置。
        mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);

        // 26.1.2 实体渲染：先 extractRenderState 再 submit（延迟提交），最后 renderAllFeatures 批量绘制。
        // submit 经 vanilla LivingEntityRenderer 触发 NeoForge RenderLivingEvent.Pre，
        // eyelib 的 RenderLivingEventAdapter 据此接管 a&s 实体渲染。
        EntityRenderState state = dispatcher.extractEntity(entity, partialTick);
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        state.shadowPieces.clear();

        FeatureRenderDispatcher featureRenderDispatcher = mc.gameRenderer.getFeatureRenderDispatcher();
        CameraRenderState cameraRenderState = new CameraRenderState();
        PoseStack entityPose = new PoseStack();
        dispatcher.submit(state, cameraRenderState, 0.0, 0.0, 0.0,
                entityPose, featureRenderDispatcher.getSubmitNodeStorage());
        featureRenderDispatcher.renderAllFeatures();
        mc.renderBuffers().bufferSource().endBatch();
        featureRenderDispatcher.clearSubmitNodes();

        mvStack.popMatrix();
        //?}
    }

    /**
     * Flushes all pending render buffers and restores the shadow rendering state.
     *
     * <p>Must be called after the last {@link #renderEntityAt} call.</p>
     *
     * @param mc the Minecraft instance
     */
    public static void endScene(Minecraft mc) {
        //? if <26.1 {
        mc.getEntityRenderDispatcher().setRenderShadow(true);

        // Restore the model-view stack saved in beginScene
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        //?} else {
        // 还原 beginScene 保存的 model-view 栈与输出重定向。
        RenderSystem.getModelViewStack().popMatrix();

        mc.renderBuffers().bufferSource().endBatch();
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;
        //?}
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

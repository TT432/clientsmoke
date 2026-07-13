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
//?}
//? if >=1.21 {
import org.joml.Matrix4fStack;
//?}
//? if >=26.1 {
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.GpuDevice;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.LightCoordsUtil;
//?}

/**
 * Renders Minecraft entities into a {@link RenderTarget} (FBO) with controlled
 * orthographic projection, inventory-style lighting, and a 30&deg; downward viewing angle.
 *
 * <p>支持 1.20.1（PoseStack + 立即模式）、1.21.1（Matrix4fStack + 立即模式）、
 * 26.1.2（GpuDevice/submit 延迟架构）三个版本，通过 Stonecutter {@code //?} 切分。</p>
 *
 * @author TT432
 */
public final class EntitySceneRenderer {

    private static final float VIEW_PITCH_DEG = 30.0F;
    private static final float ENTITY_Z = -11000.0F;

    //? if >=26.1 {
    private static final Projection PROJECTION = new Projection();
    private static final ProjectionMatrixBuffer PROJECTION_BUFFER = new ProjectionMatrixBuffer("clientsmoke-scene");
    //?}

    private EntitySceneRenderer() {}

    public static void renderEntity(Minecraft mc, RenderTarget rt, Entity entity,
                                    float scale, float yaw, int bgArgb) {
        beginScene(mc, rt, bgArgb);
        renderEntityAt(mc, entity, rt.width / 2.0F, rt.height / 2.0F, scale, yaw);
        endScene(mc);
    }

    public static void beginScene(Minecraft mc, RenderTarget rt, int bgArgb) {
        mc.renderBuffers().bufferSource().endBatch();

        //? if <1.21 {
        // 1.20.1: PoseStack model-view stack
        rt.bindWrite(true);
        float a = ((bgArgb >> 24) & 0xFF) / 255.0F;
        float r = ((bgArgb >> 16) & 0xFF) / 255.0F;
        float g = ((bgArgb >> 8) & 0xFF) / 255.0F;
        float b = (bgArgb & 0xFF) / 255.0F;
        RenderSystem.clearColor(r, g, b, a);
        RenderSystem.clearDepth(1.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        Matrix4f proj = new Matrix4f().setOrtho(0, rt.width, rt.height, 0, 1000.0F, 21000.0F);
        RenderSystem.setProjectionMatrix(proj, VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        mc.getEntityRenderDispatcher().prepare(mc.level, mc.gameRenderer.getMainCamera(), null);
        //?} elif <26.1 {
        // 1.21.1: Matrix4fStack model-view stack（getModelViewStack 返回类型变更）
        rt.bindWrite(true);
        float a1 = ((bgArgb >> 24) & 0xFF) / 255.0F;
        float r1 = ((bgArgb >> 16) & 0xFF) / 255.0F;
        float g1 = ((bgArgb >> 8) & 0xFF) / 255.0F;
        float b1 = (bgArgb & 0xFF) / 255.0F;
        RenderSystem.clearColor(r1, g1, b1, a1);
        RenderSystem.clearDepth(1.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        Matrix4f proj1 = new Matrix4f().setOrtho(0, rt.width, rt.height, 0, 1000.0F, 21000.0F);
        RenderSystem.setProjectionMatrix(proj1, VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack mvStack1 = RenderSystem.getModelViewStack();
        mvStack1.pushMatrix();
        mvStack1.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        mc.getEntityRenderDispatcher().prepare(mc.level, mc.gameRenderer.getMainCamera(), null);
        //?} else {
        // 26.1.2: 输出重定向 + 声明式清屏 + ProjectionMatrixBuffer UBO
        RenderSystem.outputColorTextureOverride = rt.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = rt.getDepthTextureView();
        GpuDevice device = RenderSystem.getDevice();
        device.createCommandEncoder().clearColorAndDepthTextures(
                rt.getColorTexture(), bgArgb, rt.getDepthTexture(), 1.0);
        PROJECTION.setupOrtho(1000.0F, 21000.0F, rt.width, rt.height, true);
        RenderSystem.setProjectionMatrix(PROJECTION_BUFFER.getBuffer(PROJECTION), ProjectionType.ORTHOGRAPHIC);
        Matrix4fStack mvStack2 = RenderSystem.getModelViewStack();
        mvStack2.pushMatrix();
        mvStack2.identity();
        RenderSystem.setShaderFog(null);
        mc.getEntityRenderDispatcher().prepare(mc.gameRenderer.getMainCamera(), null);
        //?}
    }

    public static void renderEntityAt(Minecraft mc, Entity entity,
                                      float centerX, float centerY,
                                      float scale, float yaw) {
        setEntityOrientation(entity, yaw);

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        //? if <1.21 {
        // 1.20.1: PoseStack model-view + setupLevel(Matrix4f)
        dispatcher.setRenderShadow(false);
        PoseStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushPose();
        mvStack.translate(centerX, centerY, ENTITY_Z);
        mvStack.scale(scale, scale, scale);
        Quaternionf viewRot = new Quaternionf().rotationZ((float) Math.PI);
        Quaternionf pitchRot = new Quaternionf().rotationX((float) Math.toRadians(VIEW_PITCH_DEG));
        viewRot.mul(pitchRot);
        mvStack.mulPose(viewRot);
        RenderSystem.applyModelViewMatrix();
        org.joml.Matrix4f rotOnly = new org.joml.Matrix4f().rotation(viewRot);
        Lighting.setupLevel(rotOnly);
        PoseStack entityPose = new PoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        dispatcher.render(entity, 0, 0, 0, yaw, mc.getPartialTick(),
                entityPose, bufferSource, LightTexture.FULL_BRIGHT);
        bufferSource.endBatch();
        mvStack.popPose();
        RenderSystem.applyModelViewMatrix();
        //?} elif <26.1 {
        // 1.21.1: Matrix4fStack model-view + setupLevel() 无参 + getTimer partialTick
        dispatcher.setRenderShadow(false);
        Matrix4fStack mvStackA = RenderSystem.getModelViewStack();
        mvStackA.pushMatrix();
        mvStackA.translate(centerX, centerY, ENTITY_Z);
        mvStackA.scale(scale, scale, scale);
        Quaternionf viewRotA = new Quaternionf().rotationZ((float) Math.PI);
        Quaternionf pitchRotA = new Quaternionf().rotationX((float) Math.toRadians(VIEW_PITCH_DEG));
        viewRotA.mul(pitchRotA);
        mvStackA.rotate(viewRotA);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupLevel();
        PoseStack entityPoseA = new PoseStack();
        MultiBufferSource.BufferSource bufferSourceA = mc.renderBuffers().bufferSource();
        dispatcher.render(entity, 0, 0, 0, yaw,
                mc.getTimer().getGameTimeDeltaPartialTick(false),
                entityPoseA, bufferSourceA, LightTexture.FULL_BRIGHT);
        bufferSourceA.endBatch();
        mvStackA.popMatrix();
        RenderSystem.applyModelViewMatrix();
        //?} else {
        // 26.1.2: extractEntity + submit + renderAllFeatures（延迟提交）
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Matrix4fStack mvStackB = RenderSystem.getModelViewStack();
        mvStackB.pushMatrix();
        mvStackB.translate(centerX, centerY, ENTITY_Z);
        mvStackB.scale(scale, scale, scale);
        Quaternionf viewRotB = new Quaternionf().rotationZ((float) Math.PI);
        Quaternionf pitchRotB = new Quaternionf().rotationX((float) Math.toRadians(VIEW_PITCH_DEG));
        viewRotB.mul(pitchRotB);
        mvStackB.rotate(viewRotB);
        mc.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        EntityRenderState state = dispatcher.extractEntity(entity, partialTick);
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        state.shadowPieces.clear();
        FeatureRenderDispatcher featureRenderDispatcher = mc.gameRenderer.getFeatureRenderDispatcher();
        CameraRenderState cameraRenderState = new CameraRenderState();
        PoseStack entityPoseB = new PoseStack();
        dispatcher.submit(state, cameraRenderState, 0.0, 0.0, 0.0,
                entityPoseB, featureRenderDispatcher.getSubmitNodeStorage());
        featureRenderDispatcher.renderAllFeatures();
        mc.renderBuffers().bufferSource().endBatch();
        featureRenderDispatcher.clearSubmitNodes();
        mvStackB.popMatrix();
        //?}
    }

    public static void endScene(Minecraft mc) {
        //? if <1.21 {
        mc.getEntityRenderDispatcher().setRenderShadow(true);
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        //?} elif <26.1 {
        mc.getEntityRenderDispatcher().setRenderShadow(true);
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
        //?} else {
        RenderSystem.getModelViewStack().popMatrix();
        mc.renderBuffers().bufferSource().endBatch();
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;
        //?}
    }

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

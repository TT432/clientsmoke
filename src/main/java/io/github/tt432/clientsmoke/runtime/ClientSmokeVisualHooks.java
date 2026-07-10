package io.github.tt432.clientsmoke.runtime;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;

/**
 * Optional per-test visual hooks executed by the screenshot pipeline.
 *
 * <p>Constructor-based smoke tests run before screenshot capture. Tests that need
 * to prove pixels can register hooks here; the state machine executes them while
 * it still owns the current test result.</p>
 *
 * <p><strong>Three hook types:</strong></p>
 * <ul>
 *   <li>{@link SceneRenderer} — dedicated FBO renderer: test renders its scene
 *       into a private {@link RenderTarget} with controlled background.</li>
 *   <li>{@link RenderHook} — pre-capture hook on the main framebuffer.</li>
 *   <li>{@link CaptureVerifier} — post-capture pixel analysis on the captured image.</li>
 * </ul>
 *
 * @author TT432
 */
public final class ClientSmokeVisualHooks {

    @FunctionalInterface
    public interface SceneRenderer {
        void render(Minecraft mc, RenderTarget rt) throws Exception;
    }

    @FunctionalInterface
    public interface RenderHook {
        void render(Minecraft mc) throws Exception;
    }

    @FunctionalInterface
    public interface CaptureVerifier {
        void verify(NativeImage image) throws Exception;
    }

    private static SceneRenderer sceneRenderer;
    private static RenderTarget sceneRenderTarget;
    private static RenderHook renderHook;
    private static CaptureVerifier captureVerifier;

    /**
     * When true, eyelib's RenderLivingEventAdapter should skip processing
     * to avoid interfering with entity rendering inside SceneRenderer callbacks.
     */
    private static boolean suppressRenderEvents = false;

    public static boolean isSuppressRenderEvents() {
        return suppressRenderEvents;
    }

    static void setSuppressRenderEvents(boolean v) {
        suppressRenderEvents = v;
    }

    public static void setScene(int width, int height, SceneRenderer renderer, CaptureVerifier verifier) {
        //? if modern {
        ClientSmokeVisualHooks.sceneRenderTarget = new com.mojang.blaze3d.pipeline.TextureTarget(null, width, height, true);
        //?} else {
        ClientSmokeVisualHooks.sceneRenderTarget = new com.mojang.blaze3d.pipeline.TextureTarget(width, height, true, Minecraft.ON_OSX);
        //?}
        ClientSmokeVisualHooks.sceneRenderer = renderer;
        ClientSmokeVisualHooks.captureVerifier = verifier;
    }

    public static void set(RenderHook renderHook, CaptureVerifier captureVerifier) {
        ClientSmokeVisualHooks.renderHook = renderHook;
        ClientSmokeVisualHooks.captureVerifier = captureVerifier;
    }

    static RenderTarget getSceneRenderTarget() {
        return sceneRenderTarget;
    }

    static void renderScene(Minecraft mc) throws Exception {
        if (sceneRenderer != null && sceneRenderTarget != null) {
            sceneRenderer.render(mc, sceneRenderTarget);
        }
    }

    static void renderBeforeCapture(Minecraft mc) throws Exception {
        if (renderHook != null) {
            renderHook.render(mc);
        }
    }

    static void verifyCapture(NativeImage image) throws Exception {
        if (captureVerifier != null) {
            captureVerifier.verify(image);
        }
        clear();
    }

    static void clear() {
        renderHook = null;
        captureVerifier = null;
        if (sceneRenderTarget != null) {
            sceneRenderTarget.destroyBuffers();
            sceneRenderTarget = null;
        }
        sceneRenderer = null;
    }

    private ClientSmokeVisualHooks() {}
}

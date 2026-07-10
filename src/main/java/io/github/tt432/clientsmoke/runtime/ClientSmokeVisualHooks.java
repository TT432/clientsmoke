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
 *       into a private {@link RenderTarget} with controlled background.
 *       The state machine captures from this FBO instead of the main framebuffer,
 *       eliminating all background noise (sky, terrain, clouds).</li>
 *   <li>{@link RenderHook} — legacy pre-capture hook on the main framebuffer.
 *       Runs before screenshot capture; useful for HUD overlay tests.</li>
 *   <li>{@link CaptureVerifier} — post-capture pixel analysis on the captured image.</li>
 * </ul>
 */
public final class ClientSmokeVisualHooks {

    /**
     * Dedicated FBO scene renderer.
     *
     * <p>The test receives a {@link RenderTarget} of the specified dimensions.
     * It is responsible for:</p>
     * <ol>
     *   <li>Binding the FBO ({@code rt.bindWrite(true)})</li>
     *   <li>Clearing to a known background color</li>
     *   <li>Rendering entities/models into the FBO via eyelib's render pipeline</li>
     *   <li>Restoring the main framebuffer ({@code mc.getMainRenderTarget().bindWrite(true)})</li>
     * </ol>
     *
     * <p>The state machine then captures from {@code rt} instead of the main framebuffer.</p>
     */
    @FunctionalInterface
    public interface SceneRenderer {
        /**
         * @param mc     Minecraft instance
         * @param rt     dedicated render target (pre-allocated, test-owned dimensions)
         */
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
     * Register a dedicated FBO scene renderer.
     *
     * <p>When set, the state machine will:</p>
     * <ol>
     *   <li>Call {@code sceneRenderer.render(mc, rt)} on the render thread</li>
     *   <li>Capture from {@code rt} instead of the main framebuffer</li>
     *   <li>Call {@code captureVerifier.verify(image)} on the captured image</li>
     * </ol>
     *
     * @param width      FBO width in pixels
     * @param height     FBO height in pixels
     * @param renderer   scene renderer that fills the FBO
     * @param verifier   post-capture pixel analysis
     */
    public static void setScene(int width, int height, SceneRenderer renderer, CaptureVerifier verifier) {
        ClientSmokeVisualHooks.sceneRenderTarget = new com.mojang.blaze3d.pipeline.TextureTarget(width, height, true, Minecraft.ON_OSX);
        ClientSmokeVisualHooks.sceneRenderer = renderer;
        ClientSmokeVisualHooks.captureVerifier = verifier;
    }

    public static void set(RenderHook renderHook, CaptureVerifier captureVerifier) {
        ClientSmokeVisualHooks.renderHook = renderHook;
        ClientSmokeVisualHooks.captureVerifier = captureVerifier;
    }

    /**
     * Returns the dedicated FBO if a {@link SceneRenderer} is registered, otherwise null.
     * Called by the state machine to decide which render target to capture from.
     */
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

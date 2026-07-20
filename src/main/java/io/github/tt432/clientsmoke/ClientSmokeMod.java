package io.github.tt432.clientsmoke;

import io.github.tt432.clientsmoke.config.ClientSmokeConfig;
import io.github.tt432.clientsmoke.debug.AIDebugServer;
import io.github.tt432.clientsmoke.runtime.ClientSmokeStateMachine;
import io.github.tt432.clientsmoke.scanner.ClientSmokeScanner;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if legacy {
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
//?} else {
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
//?}

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Composition root for the Client Smoke Test framework.
 *
 * <p>This mod is <strong>client-only</strong>. The {@code @Mod} constructor fires
 * during mod construction, after scanning is complete but before the main menu appears.</p>
 *
 * @author TT432
 */
@Mod(ClientSmokeMod.MOD_ID)
public class ClientSmokeMod {

    public static final String MOD_ID = "clientsmoke";

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSmokeMod.class);

    //? if legacy {
    public ClientSmokeMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        LOGGER.info("[ClientSmoke] Mod constructing — MOD_ID={}", MOD_ID);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ClientSmokeConfig.SPEC);
        bus.addListener(this::onClientSetup);
    //?} else {
    public ClientSmokeMod(IEventBus modEventBus, ModContainer container) {
        LOGGER.info("[ClientSmoke] Mod constructing — MOD_ID={}", MOD_ID);
        container.registerConfig(ModConfig.Type.COMMON, ClientSmokeConfig.SPEC);
        NeoForge.EVENT_BUS.register(new MinimizeOnTitleScreen());
    //?}

        // AI 调试 HTTP 服务器：仅在配置 ai_debug_port（系统属性/环境变量）时开启
        AIDebugServer.startIfConfigured();

        var discoveredTests = ClientSmokeScanner.scan();
        ClientSmokeStateMachine.setDiscoveredTests(discoveredTests);
        LOGGER.info("[ClientSmoke] {} test(s) discovered", discoveredTests.size());
    }

    //? if legacy {

    private void onClientSetup(FMLClientSetupEvent event) {
        if (!ClientSmokeConfig.isEnabled()) {
            if (ClientSmokeConfig.isPreventMouseGrab() || ClientSmokeConfig.isMinimizeWindow()) {
                event.enqueueWork(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    long window = mc.getWindow().getWindow();
                    if (ClientSmokeConfig.isPreventMouseGrab()) {
                        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
                        LOGGER.info("[ClientSmoke] Mouse cursor released (standalone)");
                    }
                    if (ClientSmokeConfig.isMinimizeWindow()) {
                        Object listener = new Object() {
                            @SubscribeEvent
                            public void onTick(TickEvent.ClientTickEvent e) {
                                if (e.phase != TickEvent.Phase.END) return;
                                if (mc.screen instanceof TitleScreen) {
                                    GLFW.glfwIconifyWindow(mc.getWindow().getWindow());
                                    LOGGER.info("[ClientSmoke] Window minimized (standalone)");
                                    MinecraftForge.EVENT_BUS.unregister(this);
                                }
                            }
                        };
                        MinecraftForge.EVENT_BUS.register(listener);
                    }
                });
            }
            return;
        }
        event.enqueueWork(() -> {
            long window = Minecraft.getInstance().getWindow().getWindow();
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            LOGGER.info("[ClientSmoke] Mouse cursor released for automated smoke run");
        });
    }
    //?} else {

    /**
     * Releases the mouse cursor. Called by the state machine's post-tick handler
     * on every tick to prevent MC from re-grabbing the mouse.
     */
    public static void releaseMouse(Minecraft mc) {
        GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        if (mc.mouseHandler.isMouseGrabbed()) {
            mc.mouseHandler.releaseMouse();
        }
    }

    /**
     * Minimizes the window when the title screen appears (standalone mode only).
     * Registered unconditionally; checks config inside the handler.
     */
    private static final class MinimizeOnTitleScreen {
        @SubscribeEvent
        public void onTick(ClientTickEvent.Post event) {
            if (ClientSmokeConfig.isEnabled()) return;
            if (!ClientSmokeConfig.isMinimizeWindow()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof TitleScreen) {
                GLFW.glfwIconifyWindow(mc.getWindow().getWindow());
                NeoForge.EVENT_BUS.unregister(this);
            }
        }
    }
    //?}
}

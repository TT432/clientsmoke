package io.github.tt432.clientsmoke;

import io.github.tt432.clientsmoke.config.ClientSmokeConfig;
import io.github.tt432.clientsmoke.runtime.ClientSmokeStateMachine;
import io.github.tt432.clientsmoke.scanner.ClientSmokeScanner;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge 1.21.1 composition root for the Client Smoke Test framework.
 *
 * <p>This mod is <strong>client-only</strong> — it will not load on dedicated servers.
 * The {@code @Mod} constructor fires during NeoForge's mod construction phase, after
 * {@code ModFileScanData} scanning is complete but before the main menu appears.</p>
 *
 * <h3>Constructor wiring (Phases 1-4)</h3>
 * <ol>
 *   <li><strong>Phase 1 (Plan 04):</strong> Register {@code ForgeConfigSpec} (config)</li>
 *   <li><strong>Phase 1 (Plan 05):</strong> Initialize scanner (annotation discovery)</li>
 *   <li><strong>Phase 2 (Plan 01):</strong> State machine created — scanner results passed to {@code ClientSmokeStateMachine}</li>
 *   <li><strong>Phase 2 (Plan 02):</strong> World creation + stabilization implementation</li>
 * </ol>
 *
 * <p>Per D-09: Scanning happens in the constructor body — not in a static block or
 * event listener. This ensures scanning occurs at mod construction time, after FML
 * has finished its own scans.</p>
 *
 * @author TT432
 */
@Mod(ClientSmokeMod.MOD_ID)
public class ClientSmokeMod {

    /** Unique mod identifier — must match {@code mods.toml [[mods]].modId}. */
    public static final String MOD_ID = "clientsmoke";

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSmokeMod.class);

    public ClientSmokeMod(IEventBus bus, ModContainer container) {
        LOGGER.info("[ClientSmoke] Mod constructing — MOD_ID={}", MOD_ID);

        container.registerConfig(ModConfig.Type.COMMON, ClientSmokeConfig.SPEC);
        bus.addListener(this::onClientSetup);

        var discoveredTests = ClientSmokeScanner.scan();
        ClientSmokeStateMachine.setDiscoveredTests(discoveredTests);
        LOGGER.info("[ClientSmoke] Phase 2 ready — {} test(s) discovered, state machine will activate on first client tick", discoveredTests.size());
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        if (!ClientSmokeConfig.isEnabled()) {
            return;
        }
        event.enqueueWork(() -> {
            long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            LOGGER.info("[ClientSmoke] Mouse cursor released for automated smoke run");
        });
    }
}

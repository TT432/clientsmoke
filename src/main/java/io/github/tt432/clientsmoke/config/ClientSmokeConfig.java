package io.github.tt432.clientsmoke.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration for the Client Smoke Test framework.
 *
 * <p>Uses {@link ForgeConfigSpec} (Forge) / {@code ModConfigSpec} (NeoForge) —
 * the package name is swapped by Stonecutter replacement for neoforge versions.
 * Config is registered as {@code COMMON} and persisted to
 * {@code config/clientsmoke-common.toml} in the game directory.</p>
 *
 * <p><strong>Master switch:</strong> When {@code ENABLED} is {@code false} (default),
 * the entire framework is silent.</p>
 *
 * @author TT432
 */
public final class ClientSmokeConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment(
                    "Master switch for the client smoke test framework.",
                    "When false (default): no scanning, no tick handler, no events — completely silent.",
                    "When true: annotation scanning activates at mod construction, state machine starts on first tick."
            )
            .define("enabled", false);

    public static final ForgeConfigSpec.IntValue SCREENSHOT_DELAY = BUILDER
            .comment(
                    "Delay in seconds after world load before first screenshot capture.",
                    "Default: 5 seconds (100 ticks at 20 TPS)"
            )
            .defineInRange("screenshotDelay", 5, 0, 120);

    public static final ForgeConfigSpec.IntValue RELOAD_STABILIZE_TICKS = BUILDER
            .comment(
                    "Number of ticks to wait after player spawn for render stabilization.",
                    "Default: 40 ticks (2 seconds at 20 TPS)"
            )
            .defineInRange("reloadStabilizeTicks", 40, 0, 200);

    public static final ForgeConfigSpec.BooleanValue EXIT_AFTER_SMOKE = BUILDER
            .comment(
                    "Automatically exit Minecraft after all smoke tests complete.",
                    "Default: true"
            )
            .define("exitAfterSmoke", true);

    public static final ForgeConfigSpec.IntValue REPORT_RETENTION_COUNT = BUILDER
            .comment(
                    "Maximum number of historical reports to keep.",
                    "Set to 0 to keep all reports indefinitely.",
                    "Default: 5"
            )
            .defineInRange("reportRetentionCount", 5, 0, 100);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean isEnabled() {
        String prop = System.getProperty("clientsmoke.enabled");
        if (prop != null) return Boolean.parseBoolean(prop);
        return ENABLED.get();
    }

    public static boolean shouldExitAfterSmoke() {
        String prop = System.getProperty("clientsmoke.autoExit");
        if (prop != null) return Boolean.parseBoolean(prop);
        return EXIT_AFTER_SMOKE.get();
    }

    public static boolean isPreventMouseGrab() {
        String prop = System.getProperty("eyelib.preventMouseGrab");
        if (prop != null) return Boolean.parseBoolean(prop);
        return false;
    }

    public static boolean isMinimizeWindow() {
        String prop = System.getProperty("eyelib.minimizeWindow");
        if (prop != null) return Boolean.parseBoolean(prop);
        return false;
    }

    private ClientSmokeConfig() {}
}

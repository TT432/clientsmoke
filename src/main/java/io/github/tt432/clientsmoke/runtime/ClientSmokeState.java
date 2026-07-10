package io.github.tt432.clientsmoke.runtime;

/**
 * Tick-driven state machine states for the {@link ClientSmokeStateMachine}.
 *
 * <p>The state machine consumes this enum via a {@code switch} statement in the
 * tick handler. Each tick processes exactly one state transition.
 * Terminal states ({@link #IDLE}, {@link #ERROR}) halt further processing.</p>
 *
 * @author TT432
 */
public enum ClientSmokeState {

    /** Entry point. Checks {@code clientsmoke.enabled}. */
    INIT,

    /** Terminal state when the framework is disabled. */
    IDLE,

    /** Config verification. Logs current config values. */
    CONFIG_LOAD,

    /** Scanner results verification. */
    SCAN,

    /** World creation or reuse. Creates a creative superflat world. */
    WORLD_CREATE,

    /** Player spawn wait. Polls {@code Minecraft.getInstance().player}. */
    WORLD_WAIT,

    /** Render stabilization delay. */
    STABILIZE,

    /** Pre-screenshot HUD hiding. Sets {@code hideGui = true}. */
    HUD_HIDE,

    /** Screenshot capture on the render thread. */
    SCREENSHOT,

    /** Test execution phase. Loads and instantiates the test class. */
    TEST_EXEC,

    /** Delay countdown between test execution and screenshot capture. */
    REPOSITION,

    /** Report generation. Serializes accumulated results to JSON + JUnit XML. */
    REPORT,

    /** Graceful two-phase JVM exit. */
    EXIT,

    /** Terminal state for failure recovery. */
    ERROR
}

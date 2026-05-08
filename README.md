# ClientSmoke

ClientSmoke is a standalone NeoForge client-side smoke test mod for automated visual and runtime checks in a real Minecraft client.

It discovers classes annotated with `@ClientSmoke`, creates a deterministic client test world, runs the discovered tests after the world is stable, captures screenshots, and writes machine-readable reports.

## 1. Use With Composite Build And Git Submodule

Add ClientSmoke as a Git submodule in the consuming mod repository:

```bash
git submodule add https://github.com/TT432/clientsmoke clientsmoke
git submodule update --init --recursive
```

Wire it into the consuming repository with a Gradle composite build in `settings.gradle`:

```gradle
includeBuild("clientsmoke")
```

Then depend on the ClientSmoke artifact by coordinates, not by `project(...)`:

```gradle
dependencies {
    compileOnly "io.github.tt432:clientsmoke:${version}"
    localRuntime "io.github.tt432:clientsmoke:${version}"
}
```

Use `compileOnly` when the main mod only needs the `@ClientSmoke` annotation at compile time. Use `localRuntime` so the ClientSmoke mod is present only in development smoke runs and is not published as a normal runtime dependency of the main mod.

For a smoke target module, depend on the same coordinate:

```gradle
dependencies {
    implementation "io.github.tt432:clientsmoke:${project.version}"
    implementation project(":your-feature-module")
}
```

This keeps ClientSmoke independent from the mod being tested while still letting the target module register tests and call helper APIs such as `ClientSmokeVisualHooks`.

## 2. Create A ModDevGradle Run Configuration

With ModDevGradle, define a dedicated client run in the consuming mod's `neoForge.runs` block:

```gradle
neoForge {
    runs {
        clientSmoke {
            client()
            gameDirectory = project.file("run/clientsmoke")
            systemProperty "clientsmoke.enabled", "true"
            systemProperty "clientsmoke.autoExit", "true"
        }
    }
}
```

Recommended conventions:

- Use a separate `run/clientsmoke` game directory so smoke artifacts and generated config do not pollute normal dev runs.
- Set `clientsmoke.enabled=true` only on the smoke run configuration.
- Set `clientsmoke.autoExit=true` for CI-style runs where the Minecraft process should terminate after reports are written.
- Keep normal `runClient` free of ClientSmoke system properties so regular development is unaffected.

After Gradle sync, the run configuration is usually exposed as `runClientSmoke`.

## 3. What This Mod Does

ClientSmoke runs client-only checks inside an actual Minecraft client instead of a JVM-only unit test.

At startup, it:

- Registers its config.
- Scans all loaded mod files for `@ClientSmoke` using NeoForge `ModFileScanData` and ASM metadata.
- Discovers tests without loading test classes during scanning.

When enabled, it:

- Creates or opens a deterministic flat smoke-test world.
- Waits for the client world and player to stabilize.
- Runs discovered smoke test classes in priority order.
- Allows tests to render custom visuals before screenshot capture through `ClientSmokeVisualHooks.renderBeforeCapture(...)`.
- Captures framebuffer screenshots.
- Allows tests to verify captured pixels through `ClientSmokeVisualHooks.verifyCapture(...)`.
- Writes JSON and JUnit XML reports under `run/clientsmoke/clientsmoke-reports/`.
- Exits the client process with code `0` on success or `1` on smoke failure when `clientsmoke.autoExit=true`.

ClientSmoke is intentionally generic. It should own discovery, lifecycle, screenshots, reporting, and visual hook plumbing. Feature-specific assertions and fixtures should live in the consuming project or a dedicated smoke target module.

## 4. Example

Suppose a mod has a material pipeline module and wants to verify that red, green, and translucent materials render correctly in a live client.

Create a small smoke target module, for example `your-material-smoke`, and add:

```gradle
dependencies {
    implementation "io.github.tt432:clientsmoke:${project.version}"
    implementation project(":your-material")
}
```

Then create a smoke test class:

```java
package com.example.materialsmoke;

import io.github.tt432.clientsmoke.runtime.ClientSmokeVisualHooks;
import io.github.tt432.clientsmokeannotation.ClientSmoke;

@ClientSmoke(
        description = "Validates material framebuffer output",
        priority = 10,
        modId = "examplematerial"
)
public final class MaterialPipelineSmoke {

    public MaterialPipelineSmoke() {
        ClientSmokeVisualHooks.renderBeforeCapture(() -> {
            // Bind shaders, set material states, and draw deterministic quads.
        });

        ClientSmokeVisualHooks.verifyCapture(image -> {
            // Sample pixels from known framebuffer regions.
            // Throw AssertionError if expected colors are missing.
        });
    }
}
```

Run the dedicated smoke configuration:

```bash
./gradlew runClientSmoke
```

For this repository, Gradle tasks are run through the IDE/MCP tooling rather than directly from shell, but the run configuration and task name are the same.

Expected output artifacts:

```text
run/clientsmoke/clientsmoke-reports/report-<timestamp>.json
run/clientsmoke/clientsmoke-reports/junit-<timestamp>.xml
run/clientsmoke/clientsmoke-reports/screenshots/MaterialPipelineSmoke-<timestamp>.png
```

The JSON report records total, passed, failed, timestamp, class name, description, priority, status, and duration. The JUnit XML report can be consumed by CI systems. Screenshots provide visual evidence for manual review and debugging.

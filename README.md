<img src="assets/icon.svg" width="64" height="64" alt="">

# T3 Code Host (Unofficial)

JetBrains IDE plugin that shows the [T3 Code](https://t3.codes) web UI in a tool window.
This plugin is not affiliated with or endorsed by the T3 Code project.

- Plan: [`PLAN.md`](PLAN.md)
- Phase 0 spike results: [`docs/spike-phase-0.md`](docs/spike-phase-0.md)

## Requirements

- IntelliJ Platform 2026.2 or later (since-build `262`).
- Java 25 to compile. Gradle can run on Java 21 or later.
- T3 Code with the `t3` CLI on the same machine.

## Build and run

```bash
./gradlew buildPlugin   # ZIP in build/distributions
./gradlew runIde        # sandbox IntelliJ IDEA with the plugin
./gradlew runPyCharm    # sandbox PyCharm with the plugin
./gradlew check         # tests
./gradlew verifyPlugin  # Plugin Verifier

# Tests with the real t3 CLI, on an isolated T3 home and a free port
T3_CLI_IT=1 ./gradlew test --tests com.bicf.t3plugin.T3CliIntegrationTest
```

On Linux with `kernel.apparmor_restrict_unprivileged_userns = 1` (Ubuntu 24.04 and later), the sandbox IDE shows
"Embedded Browser is suspended". Click "Enable Browser…", or set the registry key `ide.browser.jcef.sandbox.enable` to `false` in the sandbox IDE.

## Settings

Settings | Tools | T3 Code:

- **t3 command:** empty means `<T3 home>/bin`, `~/.t3/bin`, then `PATH`.
- **Remote server URL:** empty means the T3 server on this machine. With a URL, the tool window asks for a link from `t3 pair` on that host.
- **Start t3 serve when no local server answers:** on by default. While this server runs, the T3 desktop app cannot open the same T3 home.

---
Plugin based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).

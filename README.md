<img src="assets/icon.svg" width="64" height="64" alt="">

# T3 Code Host (Unofficial)

JetBrains IDE plugin that shows the [T3 Code](https://t3.codes) web UI in the IDE.
The "T3 Code" tool window shows the T3 sidebar (projects and threads). Each conversation opens in an editor tab, without the T3 sidebars;
the right panel of T3 (browser, device, pull requests, side chat) opens in the "T3 Workspace" tool window.
File links, "Open in…", diffs, the terminal, the file panel and Git use the IDE tools, when the IDE has them.
This plugin is not affiliated with or endorsed by the T3 Code project.

- Plan: [`PLAN.md`](PLAN.md)
- Phase 0 spike results: [`docs/spike-phase-0.md`](docs/spike-phase-0.md)
- Names of the UI objects (IDE window, T3 sidebar, editor tabs, message rows, composer): [`docs/ui-map.svg`](docs/ui-map.svg)

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
- **Open conversations in editor tabs:** on by default. Off: the tool window shows the full T3 web UI, as in version 0.0.3.
- **Use the IDE tools in T3:** on by default. Off: T3 uses its own file viewer, diff, terminal and Git panels.

## License

T3 Code Host (Unofficial) – Copyright 2026 Ivan Buttinoni.

The plugin is free software under the [GNU General Public License v3.0](LICENSE), or any later version.
A copy or a modified version must keep the copyright and the [NOTICE](NOTICE) file, and must use the same license.

---
Plugin based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).

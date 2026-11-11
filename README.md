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
./gradlew runIde        # sandbox IDE with the plugin
./gradlew check         # tests
./gradlew verifyPlugin  # Plugin Verifier
```

---
Plugin based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).

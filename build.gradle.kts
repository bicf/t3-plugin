import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // Target platform 2026.2: since-build 262 comes from this version, and compilation needs Java 25.
        intellijIdea("2026.2.3")

        // Since 2026.2 the JCEF API lives in the bundled "Web Browser (JCEF)" plugin.
        bundledPlugin("com.intellij.modules.jcef")

        // Optional dependencies (plugin.xml): the IDE terminal and Git replace the T3 terminal and Git actions.
        bundledPlugin("org.jetbrains.plugins.terminal")
        bundledPlugin("Git4Idea")

        testFramework(TestFrameworkType.Platform)
        testBundledPlugins("com.intellij.modules.ultimate")
    }
}

// Signing and publishing read the same environment variables as .github/workflows/release.yml.
// PUBLISH_CHANNEL selects the Marketplace channel ("beta", ...); without it, the plugin goes to "default".
intellijPlatform {
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.environmentVariable("PUBLISH_CHANNEL").orElse("default").map { listOf(it) }
    }
}

intellijPlatformTesting {
    runIde {
        // `./gradlew runPyCharm`: the plugin in PyCharm, the second IDE of the phase 2 exit criterion.
        register("runPyCharm") {
            type = IntelliJPlatformType.PyCharm
            version = "2026.2.4"
        }
    }
}

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

        testFramework(TestFrameworkType.Platform)
        testBundledPlugins("com.intellij.modules.ultimate")
    }
}

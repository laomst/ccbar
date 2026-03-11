plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = "com.github.ccbar"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        bundledPlugin("org.jetbrains.plugins.terminal")
        pluginVerifier()
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        name = "CCBar"
        version = "1.0.0"
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }
    // 禁用代码插桩以避免下载额外的依赖
    instrumentCode = false
    // verifyPlugin 可以单独运行：./gradlew verifyPlugin
    pluginVerification {
        ides {
            recommended()
        }
    }
    // JetBrains Marketplace 发布配置
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
            .orElse(providers.gradleProperty("intellijPlatformPublishingToken").orElse(""))
    }
}

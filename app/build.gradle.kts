plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.debz.debugqs"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "dev.debz.debugqs"
        // Bedrock's frosted glass needs Android 12.
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    // Release signing comes from ~/.gradle/gradle.properties (never from the repo):
    // DEBUGQS_STORE_FILE, DEBUGQS_STORE_PASSWORD, DEBUGQS_KEY_ALIAS, DEBUGQS_KEY_PASSWORD.
    val storeFilePath = providers.gradleProperty("DEBUGQS_STORE_FILE").orNull
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("DEBUGQS_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("DEBUGQS_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("DEBUGQS_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (storeFilePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.bedrock)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}

// Keeps screens on the Bedrock design system: tokens only, no Material theme, no ripples.
val verifyDesignRules = tasks.register("verifyDesignRules") {
    group = "verification"
    val sources = fileTree("src/main") { include("**/*.kt") }
    inputs.files(sources)
    doLast {
        val rules = listOf(
            Regex("""\bMaterialTheme\.""") to "use BedrockTheme.colors / typography",
            Regex("""\.(combinedC|c)lickable\s*[({]""") to "use Modifier.pressable { }",
            Regex("""\bripple\s*\(""") to "no ripples; use Modifier.pressable",
            Regex("""\bColor\s*\(\s*0x""") to "hard-coded colour; use a token",
        )
        val problems = sources.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().flatMapIndexed { i, line ->
                val code = line.substringBefore("//")
                rules.filter { (pattern, _) -> pattern.containsMatchIn(code) }
                    .map { (_, why) -> "${file.path}:${i + 1}: $why" }
            }
        }
        if (problems.isNotEmpty()) throw GradleException("Bedrock design rules:\n" + problems.joinToString("\n"))
    }
}
tasks.named("check") { dependsOn(verifyDesignRules) }

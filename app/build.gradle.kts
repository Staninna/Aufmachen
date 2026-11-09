plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.staninna.aufmachen"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.staninna.aufmachen"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        localeFilters += listOf("en", "nl")
    }
    packaging {
        resources {
            excludes += listOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module",
                "META-INF/javamail.*", "META-INF/mailcap*")
            pickFirsts += listOf("META-INF/javamail.providers", "META-INF/javamail.default.providers",
                "META-INF/javamail.default.address.map", "META-INF/javamail.charset.map")
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Scoring parity fixture: generated from the Python prototype (mail_triage.py) before unit tests.
val parityDir = layout.buildDirectory.dir("generated/parity")
val generateParityFixture by tasks.registering(Exec::class) {
    inputs.files(rootProject.file("mail_triage.py"), rootProject.file("tools/make_parity_fixture.py"))
    outputs.dir(parityDir)
    commandLine("python3", "-I", rootProject.file("tools/make_parity_fixture.py").path,
        parityDir.get().file("parity/fixture.json").asFile.path)
}
android.sourceSets.getByName("test").resources.srcDir(file("build/generated/parity"))
tasks.matching { it.name.endsWith("UnitTestJavaRes") }
    .configureEach { dependsOn(generateParityFixture) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Storage: Room on top of SQLCipher, so header records are encrypted at rest.
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")
    implementation("net.zetetic:sqlcipher-android:4.10.0@aar")
    implementation("androidx.sqlite:sqlite-ktx:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Background sync, app lock.
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.biometric:biometric:1.1.0")

    // IMAP/SMTP (headers only, read-only folder) and Gmail's X-GM-* extensions.
    implementation("com.sun.mail:android-mail:1.6.8")
    implementation("com.sun.mail:android-activation:1.6.8")
    implementation("com.sun.mail:gimap:1.6.8") { isTransitive = false }

    // Text layer + link annotations from Tuta's PDF export. No OCR.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core-ktx:1.7.0")
}

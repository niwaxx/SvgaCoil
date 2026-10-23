plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "com.github.niwaxx.SvgaCoil"
version = "1.0.0"

android {
    namespace = "com.opensource.svga.coil"
    compileSdk {
        version = release(37)
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":svga-core"))
    api(libs.coil)
    api(libs.coil.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.vectordrawable.animated)
    implementation(libs.okio)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = project.group.toString()
                artifactId = "coil-svga"
                version = project.version.toString()
            }
        }
    }
}

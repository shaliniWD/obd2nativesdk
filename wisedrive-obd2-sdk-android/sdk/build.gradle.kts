plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

// SDK Version
val sdkVersionName = "2.0.0"
val sdkVersionCode = 200

android {
    namespace = "com.wisedrive.obd2"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        
        // Add version info to BuildConfig
        buildConfigField("String", "SDK_VERSION", "\"$sdkVersionName\"")
        buildConfigField("int", "SDK_VERSION_CODE", "$sdkVersionCode")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    buildFeatures {
        buildConfig = true
    }
    
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.core:core-ktx:1.12.0")
    
    // OkHttp for network requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// ============================================================
// MAVEN PUBLISHING CONFIGURATION
// ============================================================

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.wisedrive"
            artifactId = "obd2-sdk"
            version = sdkVersionName
            
            afterEvaluate {
                from(components["release"])
            }
            
            pom {
                name.set("WiseDrive OBD2 SDK")
                description.set("Android SDK for OBD-II vehicle diagnostics with military-grade encryption")
                url.set("https://wisedrive.in/sdk")
                
                licenses {
                    license {
                        name.set("WiseDrive Commercial License")
                        url.set("https://wisedrive.in/sdk/license")
                    }
                }
                
                developers {
                    developer {
                        id.set("wisedrive")
                        name.set("WiseDrive Technologies")
                        email.set("sdk@wisedrive.in")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/wisedrive/obd2-sdk-android.git")
                    developerConnection.set("scm:git:ssh://github.com/wisedrive/obd2-sdk-android.git")
                    url.set("https://github.com/wisedrive/obd2-sdk-android")
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "JFrogSnapshots"
            url = uri("https://wisedrive.jfrog.io/artifactory/wisedrive-sdk-snapshots")
            credentials {
                username = System.getenv("JFROG_USER") 
                    ?: project.findProperty("jfrog.user")?.toString() 
                    ?: ""
                password = System.getenv("JFROG_TOKEN") 
                    ?: project.findProperty("jfrog.token")?.toString() 
                    ?: ""
            }
        }
        maven {
            name = "JFrogReleases"
            url = uri("https://wisedrive.jfrog.io/artifactory/wisedrive-sdk-releases")
            credentials {
                username = System.getenv("JFROG_USER") 
                    ?: project.findProperty("jfrog.user")?.toString() 
                    ?: ""
                password = System.getenv("JFROG_TOKEN") 
                    ?: project.findProperty("jfrog.token")?.toString() 
                    ?: ""
            }
        }
    }
}

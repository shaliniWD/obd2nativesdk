pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        
        // WiseDrive Private Repository (Client Access)
        maven {
            url = uri("https://wisedrive.jfrog.io/artifactory/wisedrive-sdk-snapshots")
            credentials {
                val props = java.util.Properties()
                val f = file("local.properties")
                if (f.exists()) props.load(f.inputStream())
                username = "obdsdktest"
                password = props.getProperty("client.jfrog.token", "")
            }
        }
    }
}

rootProject.name = "wisedrive-obd2-sdk-android"
include(":sdk")
include(":sample")

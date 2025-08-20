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
        // ByteDance ByteRTC repository
        maven {
            url = uri("https://artifact.bytedance.com/repository/Volcengine/")
        }
        // 备用仓库
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "RunTrack"
include(":app")

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
        // 添加火山引擎RTC仓库
        maven {
            url = uri("https://artifact.bytedance.com/repository/VolcEngineRTC/")
        }
        // 备用仓库
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "RunTrack"
include(":app")

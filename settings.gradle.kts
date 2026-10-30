pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 카카오 SDK (com.kakao.sdk:*) — Maven Central 에 없고 카카오 자체 저장소 사용
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }
        // 카카오맵 SDK v2 (com.kakao.maps.open:android) 배포 저장소
        maven { url = uri("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/") }
    }
}

rootProject.name = "OctaLink"
include(":app")

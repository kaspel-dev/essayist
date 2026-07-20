pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.spring.io/milestone")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.embabel.com/artifactory/libs-snapshot") {
            mavenContent {
                snapshotsOnly()
            }
        }
        maven("https://repo.spring.io/milestone")
    }
}

rootProject.name = "essayist"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.grim.ac/snapshots") { content { includeGroup("ac.grim.grimac") } }
        // Optional local cache used by the supplied verification environment.
        if (file("../../work/m2").isDirectory) {
            maven { url = uri("../../work/m2"); content { includeGroup("io.papermc.paper"); includeGroup("com.github.retrooper") } }
        }
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/") { content { includeGroup("com.github.retrooper") } }
    }
}

rootProject.name = "ConquestSMP"

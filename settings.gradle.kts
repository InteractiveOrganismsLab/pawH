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

        // Jitpack repo needed for Nix Universal SDK cross-dependencies
        maven { url = uri("https://jitpack.io") }

        // Point to folder-based `nixrepo` repository. If you have stored
        // `nixrepo` in a repository management system, point the url to that
        // location instead
        maven { url = uri("file://$rootDir/nixrepo") }
    }
}

rootProject.name = "DogApp"
include(":app")

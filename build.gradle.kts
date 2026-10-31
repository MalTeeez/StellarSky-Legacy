plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// The version comes from the git tag: gtnhgradle's GitVersionModule uses the VERSION
// environment variable when set (the release workflow passes the tag name) and falls back to
// `git describe`. Setting it here would silently override both, so the built jar would keep
// its old number no matter what was tagged.

// StellarAPI ships inside this jar as a second @Mod. It used to be a separate Gradle build,
// but gtnhconvention cannot be applied to a subproject, so two mods cannot share one Gradle
// build. Sources stay in stellarapi/ (mirroring upstream's layout) and are compiled and
// packaged here.
sourceSets {
    main {
        java.srcDir("stellarapi/src/main/java")
    }
}

tasks.processResources {
    // StellarAPI's assets only; mcmod.info is merged by hand in src/main/resources.
    from("stellarapi/src/main/resources") {
        exclude("mcmod.info")
    }
}

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Pinned rather than derived from `git describe`, to keep matching the upstream releases.
version = "0.1.5.7"

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

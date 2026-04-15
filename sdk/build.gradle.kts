plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "com.maig"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.gson)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

// Publish to Maven Local for local integration testing
tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])
            groupId = "com.github.maig-dev"
            artifactId = "maig-android-sdk"
            version = "0.1.0"
        }
    }
}

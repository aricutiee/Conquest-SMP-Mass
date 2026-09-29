plugins {
    java
    `java-library`
    id("com.gradleup.shadow") version "8.3.6"
}

group = "dev.turtleroles"
version = "3.21.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val paperVersion = "1.21.11-R0.1-SNAPSHOT"
val sqliteVersion = "3.50.3.0"
val snakeyamlVersion = "2.4"
val junitVersion = "5.13.4"

sourceSets {
    create("packagingTest")
    create("assetGenerator") {
        java.srcDir("src/assetGenerator/java")
    }
}

configurations {
    named("assetGeneratorImplementation") {
        extendsFrom(configurations.implementation.get())
    }
}

dependencies {
    compileOnly("com.github.retrooper:packetevents-api:2.14.0") { isTransitive = false }
    testImplementation("com.github.retrooper:packetevents-api:2.14.0") { isTransitive = false }
    testImplementation("com.github.retrooper:packetevents-netty-common:2.14.0") { isTransitive = false }
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("ac.grim.grimac:GrimAPI:1.2.4.0") { isTransitive = false }
    testImplementation("ac.grim.grimac:GrimAPI:1.2.4.0") { isTransitive = false }

    implementation("org.xerial:sqlite-jdbc:$sqliteVersion")
    implementation("org.yaml:snakeyaml:$snakeyamlVersion")

    testImplementation(enforcedPlatform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.xerial:sqlite-jdbc:$sqliteVersion")
    testImplementation("org.yaml:snakeyaml:$snakeyamlVersion")
    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

val generatedPackDir = layout.buildDirectory.dir("generated/turtleroles/resource-pack")
val generatedBadgeDir = layout.buildDirectory.dir("generated/turtleroles/badges")
val generatedPreviewDir = layout.buildDirectory.dir("generated/turtleroles/previews")
val generatedZip = layout.buildDirectory.file("distributions/ConquestSMP-resource-pack.zip")
val generatedSha1 = layout.buildDirectory.file("distributions/ConquestSMP-resource-pack.sha1")

val generateRoleAssets by tasks.registering(JavaExec::class) {
    group = "turtleroles"
    description = "Generates transparent role badge PNGs, previews, font JSON and the resource-pack ZIP."
    classpath = sourceSets["assetGenerator"].runtimeClasspath
    mainClass.set("dev.turtleroles.assets.BadgeAssetGenerator")
    args(
        generatedPackDir.get().asFile.absolutePath,
        generatedBadgeDir.get().asFile.absolutePath,
        generatedPreviewDir.get().asFile.absolutePath,
        generatedZip.get().asFile.absolutePath,
        generatedSha1.get().asFile.absolutePath,
        file("design/crown").absolutePath,
        file("design/logo/conquest-smp.png").absolutePath,
        file("design/audio").absolutePath
    )
    inputs.dir("design/crown")
    inputs.dir("design/audio")
    inputs.file("design/logo/conquest-smp.png")
    outputs.dir(generatedPackDir)
    outputs.dir(generatedBadgeDir)
    outputs.dir(generatedPreviewDir)
    outputs.file(generatedZip)
    outputs.file(generatedSha1)
}

tasks.processResources {
    inputs.property("pluginVersion", project.version)
    dependsOn(generateRoleAssets)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
    from(generatedPackDir) {
        into("generated-resource-pack")
    }
    from(generatedZip) {
        into("generated-resource-pack")
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    // SQLite's JNI library references org.sqlite by name; relocating it breaks
    // native loading when the server does not provide an unrelocated driver.
    relocate("org.yaml.snakeyaml", "dev.turtleroles.libs.snakeyaml")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}


tasks.test {
    dependsOn(generateRoleAssets)
    useJUnitPlatform()
    systemProperty("turtleroles.generatedPackDir", generatedPackDir.get().asFile.absolutePath)
    systemProperty("turtleroles.generatedBadgeDir", generatedBadgeDir.get().asFile.absolutePath)
    systemProperty("turtleroles.generatedZip", generatedZip.get().asFile.absolutePath)
}

val packagedDatabaseTest by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verifies that SQLite loads and persists data from the actual shaded plugin JAR."
    dependsOn(tasks.shadowJar, tasks.named("packagingTestClasses"))
    classpath = sourceSets["packagingTest"].runtimeClasspath + files(tasks.shadowJar.flatMap { it.archiveFile })
    mainClass.set("PackagedSqliteCheck")
    val scratch = layout.buildDirectory.dir("packaging-test")
    doFirst { scratch.get().asFile.mkdirs() }
    args(scratch.get().asFile.absolutePath)
}

tasks.check {
    dependsOn(packagedDatabaseTest)
}

tasks.register("printArtifacts") {
    dependsOn(tasks.build)
    doLast {
        println("Plugin JAR: ${tasks.shadowJar.get().archiveFile.get().asFile.absolutePath}")
        println("Resource pack ZIP: ${generatedZip.get().asFile.absolutePath}")
        println("Resource pack SHA-1: ${generatedSha1.get().asFile.readText().trim()}")
        println("Badge PNGs: ${generatedBadgeDir.get().asFile.absolutePath}")
        println("Preview PNGs: ${generatedPreviewDir.get().asFile.absolutePath}")
    }
}

val lungePack by tasks.registering(Zip::class) {
    from("src/lunge-datapack")
    archiveFileName.set("conquest-lunge.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
tasks.named("assemble") { dependsOn(lungePack) }


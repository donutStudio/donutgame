import org.gradle.api.GradleException
import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.SourceSetContainer

plugins {
    java
    id("com.gradleup.shadow") version "8.3.10"
}

group = "com.donutsforlife11"
version = "1.0.0"
val paperVersion = "26.2.build.127-stable"

providers.gradleProperty("verificationBuildDir").orNull?.let { directory ->
    allprojects {
        layout.buildDirectory.set(rootProject.layout.projectDirectory.dir(directory).dir(path.removePrefix(":").replace(':', '/').ifEmpty { "framework" }))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.5")
    compileOnly("com.infernalsuite.asp:api:4.0.0-SNAPSHOT")
    implementation("fr.skytasul:glowingentities:2.0.0")
    implementation("fr.mrmicky:fastboard:2.2.1")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.infernalsuite.asp:file-loader:4.0.0-SNAPSHOT") {
        exclude(group = "com.infernalsuite.asp", module = "api")
    }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val generatedPaperFacades = layout.buildDirectory.dir("generated/sources/paper-facades/java/main")
val generatedGameWorldGamerulesApi = layout.buildDirectory.dir("generated/sources/game-world-gamerules/java/main")
val generatedGameObjectSpecsApi = layout.buildDirectory.dir("generated/sources/game-object-specs/java/main")

sourceSets {
    main {
        java.srcDir(generatedPaperFacades)
        java.srcDir(generatedGameWorldGamerulesApi)
        java.srcDir(generatedGameObjectSpecsApi)
    }
}

dependencies {
    testImplementation("io.papermc.paper:paper-api:$paperVersion")
}

val verifyScopedState by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Exercise value ownership and virtual attribute math without a running server."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.donutsforlife11.donutgame.api.player.ScopedStateChecks")
}
val verifyPersistence by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verify SQLite persistence and external module resources without a running server."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.donutsforlife11.donutgame.api.storage.PersistenceChecks")
}
val verifyPackagedPersistence by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Exercise SQLite and relocated Gson from the deployable jar."
    dependsOn(tasks.shadowJar, tasks.testClasses)
    classpath = files(tasks.shadowJar.flatMap { it.archiveFile }, sourceSets.test.get().output) +
        (sourceSets.test.get().runtimeClasspath - sourceSets.main.get().output)
    mainClass.set("com.donutsforlife11.donutgame.api.storage.PersistenceChecks")
}
val verifyEntityMechanics by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.donutsforlife11.donutgame.api.entity.EntityMechanicsChecks")
}
val verifyItemNotation by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.donutsforlife11.donutgame.api.item.ItemNotationChecks")
}
tasks.test {
    // The small state suite uses a Java main; no test engine or server is needed.
    failOnNoDiscoveredTests.set(false)
    dependsOn(verifyScopedState)
    dependsOn(verifyPersistence)
}

val paperApiSources by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    paperApiSources("io.papermc.paper:paper-api:$paperVersion:sources")
}

allprojects {
    tasks.withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
        doFirst {
            val existingArchive = archiveFile.get().asFile
            if (existingArchive.exists() && !existingArchive.delete()) {
                throw GradleException(
                    "Cannot replace ${existingArchive.absolutePath}. Close programs that may be holding it open, then rebuild."
                )
            }
        }
    }
}

tasks {
    val checkModuleApiBoundary by registering {
        group = "verification"
        description = "Verify the compiler-enforced game module boundary and its fixtures."
    }

    val verifyModuleBoundaryFixtures by registering(ModuleApiBoundaryTask::class) {
        group = "verification"
        classpath.from(sourceSets.main.get().output.classesDirs, configurations.compileClasspath)
        dependsOn(compileJava)
        fixtures.set(layout.projectDirectory.dir("src/test/module-boundary"))
    }
    checkModuleApiBoundary { dependsOn(verifyModuleBoundaryFixtures) }

    val generatePaperFacades by registering(PaperFacadeGenerateTask::class) {
        classpath.from(configurations.compileClasspath)
        outputDirectory.set(generatedPaperFacades)
    }

    val generateGameWorldGamerulesApi by registering(GameWorldGamerulesGenerateTask::class) {
        classpath.from(configurations.compileClasspath)
        outputDirectory.set(generatedGameWorldGamerulesApi)
    }

    val generateGameObjectSpecsApi by registering(GameObjectSpecsGenerateTask::class) {
        classpath.from(configurations.compileClasspath)
        sourceArchives.from(paperApiSources)
        outputDirectory.set(generatedGameObjectSpecsApi)
    }

    compileJava {
        dependsOn(generatePaperFacades, generateGameWorldGamerulesApi, generateGameObjectSpecsApi)
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.add("-Xlint:deprecation")
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand("version" to project.version)
        }
    }

    jar {
        archiveBaseName.set("donutgame")
        enabled = false
    }

    shadowJar {
        mergeServiceFiles()
        relocate("com.google.gson", "com.donutsforlife11.donutgame.libs.gson")
        archiveBaseName.set("donutgame")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())

        relocate(
            "fr.skytasul.glowingentities",
            "com.donutsforlife11.donutgame.libs.glowingentities"
        )
        relocate(
            "fr.mrmicky.fastboard",
            "com.donutsforlife11.donutgame.libs.fastboard"
        )
    }

    build {
        dependsOn(shadowJar)
    }

    check {
        dependsOn(checkModuleApiBoundary)
        dependsOn(verifyPackagedPersistence)
        dependsOn(verifyItemNotation)
        dependsOn(verifyEntityMechanics)
    }
}

subprojects {
    plugins.withId("java") {
        val main = extensions.getByType<SourceSetContainer>().named("main")
        val frameworkClasses = rootProject.extensions.getByType<SourceSetContainer>().named("main").map { it.output.classesDirs }
        val verifyModuleApi by tasks.registering(ModuleApiBoundaryTask::class) {
            group = "verification"
            description = "Reject unscoped types using javac's resolved Java types."
            sources.from(main.map { it.allJava })
            classpath.from(frameworkClasses, main.map { source ->
                source.compileClasspath.filter { it.name != "donutgame-${rootProject.version}.jar" }
            })
            dependsOn(rootProject.tasks.named("compileJava"))
        }
        tasks.named("compileJava") { dependsOn(verifyModuleApi) }
        rootProject.tasks.named("checkModuleApiBoundary") { dependsOn(verifyModuleApi) }
    }
}

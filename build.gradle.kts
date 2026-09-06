import org.gradle.api.GradleException
import org.gradle.jvm.tasks.Jar

plugins {
    java
    id("com.gradleup.shadow") version "8.3.10"
}

group = "com.donutsforlife11"
version = "1.0.0"

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.5")
    compileOnly("com.infernalsuite.asp:api:4.0.0-SNAPSHOT")
    implementation("fr.skytasul:glowingentities:2.0.0")
    implementation("fr.mrmicky:fastboard:2.2.1")
    implementation("com.infernalsuite.asp:file-loader:4.0.0-SNAPSHOT") {
        exclude(group = "com.infernalsuite.asp", module = "api")
    }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
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
    compileJava {
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
}

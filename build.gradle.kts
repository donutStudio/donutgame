import org.gradle.api.GradleException
import org.gradle.jvm.tasks.Jar

plugins {
    java
    id("com.gradleup.shadow") version "8.3.10"
}

group = "com.donutsforlife11"
version = "1.0.0"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.12")
    compileOnly("com.infernalsuite.asp:api:4.0.0-SNAPSHOT")
    implementation("fr.skytasul:glowingentities:2.0.0")
    implementation("fr.mrmicky:fastboard:2.2.1")
    implementation("com.infernalsuite.asp:file-loader:4.0.0-SNAPSHOT") {
        exclude(group = "com.infernalsuite.asp", module = "api")
    }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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
        options.release.set(21)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
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

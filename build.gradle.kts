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
    // Provided by the AdvancedSlimePaper server jar.
    compileOnly("com.infernalsuite.asp:api:4.0.0-SNAPSHOT")
    implementation("fr.skytasul:glowingentities:1.4.11")

    // NOT provided by the server jar. This must be shaded into your plugin jar.
    implementation("com.infernalsuite.asp:file-loader:4.0.0-SNAPSHOT") {
        exclude(group = "com.infernalsuite.asp", module = "api")
    }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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

        // Prevent accidentally using the unshaded jar.
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

        // Do NOT relocate com.infernalsuite.* here.
        // Your code directly imports FileLoader, so it should remain in its normal package.
    }

    build {
        dependsOn(shadowJar)
    }
}

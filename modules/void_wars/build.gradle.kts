plugins {
    java
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))

        resources.setSrcDirs(listOf("."))
        resources.include("config.yml", "events.yml", "data/**")
    }
}

dependencies {
    compileOnly(project(":"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

tasks.compileJava {
    dependsOn(":shadowJar")
}

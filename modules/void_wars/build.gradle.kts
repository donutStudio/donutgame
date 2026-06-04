plugins {
    java
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))

        resources.setSrcDirs(listOf("."))
        resources.include("config.yml")
    }
}

dependencies {
    compileOnly(project(":"))
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

tasks.compileJava {
    dependsOn(":shadowJar")
}
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
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

tasks.compileJava {
    dependsOn(":shadowJar")
    options.compilerArgs.add("-Xlint:deprecation")
}

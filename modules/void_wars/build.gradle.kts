plugins {
    java
}

dependencies {
    compileOnly(project(":"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

tasks.compileJava {
    dependsOn(":shadowJar")
    options.compilerArgs.add("-Xlint:deprecation")
}

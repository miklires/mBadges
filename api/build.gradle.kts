plugins {
    `java-library`
}

group = rootProject.group
version = rootProject.version

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
}

tasks.jar {
    archiveFileName.set("mBadges-API-${project.version}.jar")
}

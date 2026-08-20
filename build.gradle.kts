plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
    id("com.modrinth.minotaur") version "2.9.0"
}

group = "io.github.miklires"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    implementation(project(":api"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("org.xerial:sqlite-jdbc:3.47.1.0")
    compileOnly("com.mysql:mysql-connector-j:9.1.0")
    compileOnly("org.postgresql:postgresql:42.7.5")
    implementation("org.bstats:bstats-bukkit:3.1.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    jar {
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("mBadges-${project.version}.jar")
        mergeServiceFiles()
        relocate("org.bstats", "io.github.miklires.mbadges.libs.bstats")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    test {
        useJUnitPlatform()
    }
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN") ?: "")
    projectId.set(System.getenv("MODRINTH_PROJECT_ID") ?: "")
    versionNumber.set(project.version.toString())
    versionName.set("mBadges ${project.version}")
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    gameVersions.add("26.2")
    loaders.addAll("paper", "purpur", "folia")
    changelog.set(provider { file("CHANGELOG.md").readText() })
    syncBodyFrom.set(file("README.md").readText())
}

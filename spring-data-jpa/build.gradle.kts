plugins {
    kotlin("jvm")
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
    kotlin("plugin.allopen") version "2.0.21"
}

val h2Version: String by project
val junitVersion: String by project
val logbackVersion: String by project
val springBootVersion: String by project

dependencies {
    implementation(project(":domain"))
    
    // Spring Data JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:$springBootVersion")
    
    // Database
    implementation("com.h2database:h2:$h2Version")
    
    // Kotlin support
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    
    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

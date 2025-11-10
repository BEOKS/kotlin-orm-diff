plugins {
    kotlin("jvm")
}

val exposedVersion: String by project
val h2Version: String by project
val junitVersion: String by project
val logbackVersion: String by project

dependencies {
    implementation(project(":domain"))
    
    // Exposed
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    
    // Database
    implementation("com.h2database:h2:$h2Version")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
}


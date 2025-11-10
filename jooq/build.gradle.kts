plugins {
    kotlin("jvm")
}

val jooqVersion: String by project
val h2Version: String by project
val junitVersion: String by project
val logbackVersion: String by project

dependencies {
    implementation(project(":domain"))
    
    // JOOQ
    implementation("org.jooq:jooq:$jooqVersion")
    implementation("org.jooq:jooq-kotlin:$jooqVersion")
    
    // Database
    implementation("com.h2database:h2:$h2Version")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
}


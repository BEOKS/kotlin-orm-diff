plugins {
    kotlin("jvm")
    kotlin("plugin.jpa") version "2.0.21"
    kotlin("plugin.allopen") version "2.0.21"
}

val hibernateVersion: String by project
val h2Version: String by project
val junitVersion: String by project
val logbackVersion: String by project

dependencies {
    implementation(project(":domain"))
    
    // JPA / Hibernate
    implementation("org.hibernate.orm:hibernate-core:$hibernateVersion")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    implementation("jakarta.transaction:jakarta.transaction-api:2.0.1")
    
    // Database
    implementation("com.h2database:h2:$h2Version")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("ch.qos.logback:logback-classic:$logbackVersion")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}


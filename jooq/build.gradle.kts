plugins {
    kotlin("jvm")
    id("nu.studer.jooq") version "9.0"
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
    jooqGenerator("com.h2database:h2:$h2Version")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

jooq {
    version.set(jooqVersion)
    
    configurations {
        create("main") {
            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "org.h2.Driver"
                    url = "jdbc:h2:mem:testdb;MODE=PostgreSQL;INIT=RUNSCRIPT FROM 'src/test/resources/schema.sql'"
                    user = "sa"
                    password = ""
                }
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.h2.H2Database"
                        inputSchema = "PUBLIC"
                        excludes = "INFORMATION_SCHEMA.*"
                    }
                    target.apply {
                        packageName = "com.example.eshop.jooq.generated"
                        directory = "src/main/generated"
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = true
                        isFluentSetters = true
                        isKotlinNotNullPojoAttributes = true
                        isKotlinNotNullRecordAttributes = true
                        isKotlinNotNullInterfaceAttributes = true
                    }
                }
            }
        }
    }
}

// Add generated sources to main source set
sourceSets {
    main {
        java {
            srcDir("src/main/generated")
        }
    }
}

// Make sure compileKotlin depends on generateJooq
tasks.named("compileKotlin") {
    dependsOn("generateJooq")
}


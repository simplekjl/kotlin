plugins {
    kotlin("jvm")
    id("jps-compatible")
}

description = "Kotlin/Native utils"

jvmTarget = "1.6"

dependencies {
    compile(kotlinStdlibWithoutAnnotations())
}

sourceSets {
    "main" { projectDefault() }
    "test" { none() }
}

standardPublicJars()

publish()

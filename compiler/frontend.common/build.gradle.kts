plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compile(project(":compiler:psi"))
    compile(project(":compiler:container"))
    compile(commonDep("org.jetbrains:annotations"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }
}


sourceSets {
    "main" {
        projectDefault()
    }
    "test" {}
}
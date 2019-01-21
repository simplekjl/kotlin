import java.util.regex.Pattern

description = "Kotlin Mock Runtime for Tests (JPS)"

repositories {
    maven("https://teamcity.jetbrains.com/guestAuth/app/rest/builds/buildType:(id:Kotlin_dev_Compiler),number:1.3.30-dev-940,branch:default:any/artifacts/content/internal/repo")
}

val distLib by configurations.creating
val distRoot by configurations.creating
val builtins by configurations.creating

dependencies {
    distRoot("org.jetbrains.kotlin:kotlin-stdlib-minimal-for-test:$bootstrapKotlinVersion")
    builtins("org.jetbrains.kotlin:builtins:$bootstrapKotlinVersion")

    distLib("org.jetbrains.kotlin:kotlin-stdlib:$bootstrapKotlinVersion")
    distLib("org.jetbrains.kotlin:kotlin-stdlib-js:$bootstrapKotlinVersion")
    distLib("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$bootstrapKotlinVersion")
    distLib("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$bootstrapKotlinVersion")

    distLib("org.jetbrains.kotlin:kotlin-stdlib:$bootstrapKotlinVersion:sources")
    distLib("org.jetbrains.kotlin:kotlin-stdlib-js:$bootstrapKotlinVersion:sources")
    distLib("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$bootstrapKotlinVersion:sources")
    distLib("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$bootstrapKotlinVersion:sources")
}

val distDir: String by rootProject.extra
val distLibDir: File by rootProject.extra

task<Copy>("distRoot") {
    destinationDir = File(distDir)
    dependsOn(distRoot)
    from(distRoot)
    rename("-${Pattern.quote(bootstrapKotlinVersion)}", "")
}

task<Copy>("distLib") {
    destinationDir = distLibDir
    dependsOn(distLib)
    from(distLib)

    rename("-${Pattern.quote(bootstrapKotlinVersion)}", "")
}
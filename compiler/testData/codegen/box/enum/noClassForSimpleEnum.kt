// TARGET_BACKEND: JVM

// WITH_RUNTIME

private const val testPackagePrefix = ""

enum class State {
    O,
    K
}

fun box(): String {
    val field = State::class.java.getField("O")
    val className = field.get(null).javaClass.name
    if (className != "${testPackagePrefix}State") return "Fail: $className"

    return "${State.O.name}${State.K.name}"
}

// TARGET_BACKEND: JVM

// WITH_RUNTIME

private const val testPackagePrefix = ""

enum class IssueState {
    DEFAULT,
    FIXED {
        override fun ToString() = "K"
    };

    open fun ToString(): String = "O"
}

fun box(): String {
    val field = IssueState::class.java.getField("FIXED")

    val typeName = field.type.name
    if (typeName != "${testPackagePrefix}IssueState") return "Fail type name: $typeName"

    val className = field.get(null).javaClass.name
    if (className != "${testPackagePrefix}IssueState\$FIXED") return "Fail class name: $className"

    val classLoader = IssueState::class.java.classLoader
    classLoader.loadClass("${testPackagePrefix}IssueState\$FIXED")
    try {
        classLoader.loadClass("${testPackagePrefix}IssueState\$DEFAULT")
        return "Fail: no class should have been generated for DEFAULT"
    }
    catch (e: Exception) {
        // ok
    }

    return IssueState.DEFAULT.ToString() + IssueState.FIXED.ToString()
}

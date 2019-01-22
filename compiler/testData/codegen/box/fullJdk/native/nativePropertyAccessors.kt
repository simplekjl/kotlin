// TARGET_BACKEND: JVM

// FULL_JDK

private const val testPackagePrefix = ""

class C {
    companion object {
        val defaultGetter: Int = 1
            external get

        var defaultSetter: Int = 1
            external get
            external set
    }

    val defaultGetter: Int = 1
        external get

    var defaultSetter: Int = 1
        external get
        external set
}

val defaultGetter: Int = 1
    external get

var defaultSetter: Int = 1
    external get
    external set

fun check(body: () -> Unit, signature: String): String? {
    try {
        body()
        return "Link error expected"
    }
    catch (e: java.lang.UnsatisfiedLinkError) {
        if (e.message != signature) return "Fail $signature: " + e.message
    }

    return null
}

fun box(): String {
    return check({defaultGetter}, "${testPackagePrefix}NativePropertyAccessorsKt.getDefaultGetter()I")
           ?: check({defaultSetter = 1}, "${testPackagePrefix}NativePropertyAccessorsKt.setDefaultSetter(I)V")

           ?: check({C.defaultGetter}, "${testPackagePrefix}C\$Companion.getDefaultGetter()I")
           ?: check({C.defaultSetter = 1}, "${testPackagePrefix}C\$Companion.setDefaultSetter(I)V")

           ?: check({C().defaultGetter}, "${testPackagePrefix}C.getDefaultGetter()I")
           ?: check({C().defaultSetter = 1}, "${testPackagePrefix}C.setDefaultSetter(I)V")

           ?: "OK"
}

// IGNORE_BACKEND: JVM_IR
// IGNORE_BACKEND: JS_IR
// TODO: muted automatically, investigate should it be ran for JS or not
// IGNORE_BACKEND: JS, NATIVE

// WITH_REFLECT

import kotlin.reflect.full.*

private const val testPackagePrefix = ""

class A {
    var String.id: String
        get() = this
        set(value) {}

    fun Int.foo(): Double = toDouble()
}

fun box(): String {
    val p = A::class.memberExtensionProperties.single()
    return if ("$p" == "var ${testPackagePrefix}A.(kotlin.String.)id: kotlin.String") "OK" else "Fail $p"

    val q = A::class.declaredFunctions.single()
    if ("$q" != "fun ${testPackagePrefix}A.(kotlin.Int.)foo(): kotlin.Double") return "Fail q $q"

    return "OK"
}

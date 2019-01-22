// IGNORE_BACKEND: JVM_IR
// IGNORE_BACKEND: JS_IR, JS, NATIVE
// WITH_REFLECT

import kotlin.test.assertEquals

private const val testPackagePrefix = ""

enum class E { X, Y, Z }

fun box(): String {
    assertEquals("fun values(): kotlin.Array<${testPackagePrefix}E>", E::values.toString())
    assertEquals(listOf(E.X, E.Y, E.Z), E::values.call().toList())
    assertEquals("fun valueOf(kotlin.String): ${testPackagePrefix}E", E::valueOf.toString())
    assertEquals(E.Y, E::valueOf.call("Y"))

    return "OK"
}

// IGNORE_BACKEND: JVM_IR, JS_IR, JS, NATIVE
// WITH_REFLECT

import kotlin.test.assertEquals

private const val testPackagePrefix = ""

interface I1 {
    fun f()
    val x: Int
}

interface I2 {
    fun f()
    val x: Int
}

interface I3 {
    fun f()
    val x: Int
}

interface I : I2, I1, I3

fun box(): String {
    assertEquals("fun ${testPackagePrefix}I.f(): kotlin.Unit", I::f.toString())
    assertEquals("val ${testPackagePrefix}I.x: kotlin.Int", I::x.toString())

    val f = I::class.members.single { it.name == "f" }
    assertEquals("fun ${testPackagePrefix}I.f(): kotlin.Unit", f.toString())
    val x = I::class.members.single { it.name == "x" }
    assertEquals("val ${testPackagePrefix}I.x: kotlin.Int", x.toString())

    return "OK"
}

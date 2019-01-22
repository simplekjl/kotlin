// IGNORE_BACKEND: JVM_IR
// IGNORE_BACKEND: JS_IR
// TODO: muted automatically, investigate should it be ran for JS or not
// IGNORE_BACKEND: JS, NATIVE

// WITH_REFLECT
// KT-13700 Exception obtaining descriptor for property reference

import kotlin.test.assertEquals

private const val testPackagePrefix = ""

interface H<T> {
    val parent : T?
}

interface A : H<A>

fun box(): String {
    assertEquals("${testPackagePrefix}A?", A::parent.returnType.toString())
    assertEquals("T?", H<A>::parent.returnType.toString())

    return "OK"
}

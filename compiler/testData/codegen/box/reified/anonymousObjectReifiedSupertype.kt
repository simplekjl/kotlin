// IGNORE_BACKEND: JVM_IR
// TARGET_BACKEND: JVM

// WITH_RUNTIME

import kotlin.test.assertEquals

private const val testPackagePrefix = ""

abstract class A<R> {
    abstract fun f(): String
}

inline fun<reified T> foo(): A<T> {
    return object : A<T>() {
        override fun f(): String {
            return "OK"
        }
    }
}

fun box(): String {
    val y = foo<String>();
    assertEquals("OK", y.f())
    assertEquals("${testPackagePrefix}A<java.lang.String>", y.javaClass.getGenericSuperclass()?.toString())
    return "OK"
}

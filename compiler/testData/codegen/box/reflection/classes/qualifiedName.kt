// IGNORE_BACKEND: JVM_IR
// TARGET_BACKEND: JVM

// WITH_REFLECT

import kotlin.test.assertEquals

private const val testPackagePrefix = ""

class Klass {
    class Nested
    companion object
}

fun box(): String {
    assertEquals("${testPackagePrefix}Klass", Klass::class.qualifiedName)
    assertEquals("${testPackagePrefix}Klass.Nested", Klass.Nested::class.qualifiedName)
    assertEquals("${testPackagePrefix}Klass.Companion", Klass.Companion::class.qualifiedName)

    class Local
    assertEquals(null, Local::class.qualifiedName)

    val o = object {}
    assertEquals(null, o.javaClass.kotlin.qualifiedName)

    return "OK"
}

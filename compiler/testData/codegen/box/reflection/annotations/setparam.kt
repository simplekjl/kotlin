// WITH_REFLECT
// IGNORE_BACKEND: JVM_IR, JS_IR, JS, NATIVE

import kotlin.test.assertEquals

private const val testPackagePrefix = ""

annotation class Ann1
annotation class Ann2

class Foo {
    @setparam:Ann1
    var delegate = " "
        set(@Ann2 value) {}
}

fun box(): String {
    val setterParameters = Foo::delegate.setter.parameters
    assertEquals(2, setterParameters.size)
    assertEquals("[]", setterParameters.first().annotations.toString())
    assertEquals("[@${testPackagePrefix}Ann2(), @${testPackagePrefix}Ann1()]", setterParameters.last().annotations.toString())
    return "OK"
}

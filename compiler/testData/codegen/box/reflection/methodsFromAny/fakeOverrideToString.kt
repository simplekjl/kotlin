// IGNORE_BACKEND: JVM_IR, JS_IR, JS, NATIVE
// WITH_REFLECT

import kotlin.test.assertEquals

private const val testPackagePrefix = ""

open class A<T> {
    fun foo(t: T) {}
}

open class B<U> : A<U>()

class C : B<String>()

fun box(): String {
    assertEquals("fun ${testPackagePrefix}A<T>.foo(T): kotlin.Unit", A<Double>::foo.toString())
    assertEquals("fun ${testPackagePrefix}B<U>.foo(U): kotlin.Unit", B<Float>::foo.toString())
    assertEquals("fun ${testPackagePrefix}C.foo(kotlin.String): kotlin.Unit", C::foo.toString())

    val afoo = A::class.members.single { it.name == "foo" }
    assertEquals("fun ${testPackagePrefix}A<T>.foo(T): kotlin.Unit", afoo.toString())
    val bfoo = B::class.members.single { it.name == "foo" }
    assertEquals("fun ${testPackagePrefix}B<U>.foo(U): kotlin.Unit", bfoo.toString())
    val cfoo = C::class.members.single { it.name == "foo" }
    assertEquals("fun ${testPackagePrefix}C.foo(kotlin.String): kotlin.Unit", cfoo.toString())

    return "OK"
}

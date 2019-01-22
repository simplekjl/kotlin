// EXPECTED_REACHABLE_NODES: 1293
package foo

// CHECK_NOT_CALLED: test
// CHECK_NOT_CALLED: fn

private const val testPackagePrefix = ""

class A(val x: Any? = null) {
    inline fun <reified T, reified R> test(b: B) = b.fn<T, R>()

    inline fun <reified T, reified R> B.fn() = x is T && y is R
}

class B(val y: Any? = null)

class X
class Y

fun box(): String {
    val x = X()
    val y = Y()

    assertEquals(true, A(x).test<X, Y>(B(y)), "${testPackagePrefix}A(x).test<X, Y>(${testPackagePrefix}B(y))")
    assertEquals(false, A(y).test<X, Y>(B(y)), "${testPackagePrefix}A(y).test<X, Y>(${testPackagePrefix}B(y))")
    assertEquals(false, A(y).test<X, Y>(B(x)), "${testPackagePrefix}A(y).test<X, Y>(${testPackagePrefix}B(x))")
    assertEquals(false, A(x).test<X, Y>(B(x)), "${testPackagePrefix}A(x).test<X, Y>(${testPackagePrefix}B(x))")

    return "OK"
}
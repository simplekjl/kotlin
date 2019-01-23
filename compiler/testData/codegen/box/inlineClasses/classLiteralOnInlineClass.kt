// WITH_REFLECT
// TARGET_BACKEND: JVM
// IGNORE_BACKEND: JVM_IR

package test.root

import kotlin.reflect.KClass

inline class IcInt(val x: Int)
inline class IcLong(val l: Long)
inline class IcAny(val a: Any?)
inline class IcOverIc(val o: IcLong)

fun check(c: KClass<*>, s: String) {
    if (c.toString() != s) error("Fail, expected: $s, actual: $c")
}

fun check(actual: String?, expected: String) {
    if (actual != expected) error("Fail, expected: $expected, actual: $actual")
}

inline fun <reified T> reifiedCheck(asString: String, simpleName: String) {
    check(T::class, asString)
    check(T::class.simpleName, simpleName)
}

fun box(): String {
    val i = IcInt(0)
    val l = IcLong(0)
    val a = IcAny("foo")
    val o = IcOverIc(IcLong(0))

    check(i::class, "class test.root.IcInt")
    check(l::class, "class test.root.IcLong")
    check(a::class, "class test.root.IcAny")
    check(o::class, "class test.root.IcOverIc")
    check(1u::class, "class kotlin.UInt")

    check(i::class.simpleName, "IcInt")
    check(l::class.simpleName, "IcLong")
    check(a::class.simpleName, "IcAny")
    check(o::class.simpleName, "IcOverIc")
    check(1u::class.simpleName, "UInt")

    reifiedCheck<IcInt>("class test.root.IcInt", "IcInt")
    reifiedCheck<IcLong>("class test.root.IcLong", "IcLong")
    reifiedCheck<IcAny>("class test.root.IcAny", "IcAny")
    reifiedCheck<IcOverIc>("class test.root.IcOverIc", "IcOverIc")
    reifiedCheck<UInt>("class kotlin.UInt", "UInt")

    val arrI = arrayOf(i)
    check(arrI[0]::class, "class test.root.IcInt")

    val arrL = arrayOf(l)
    check(arrL[0]::class, "class test.root.IcLong")

    val arrA = arrayOf(a)
    check(arrA[0]::class, "class test.root.IcAny")

    val arrO = arrayOf(o)
    check(arrO[0]::class, "class test.root.IcOverIc")

    val arrU = arrayOf(1u)
    check(arrU[0]::class, "class kotlin.UInt")

    check(IcInt::class, "class test.root.IcInt")
    check(IcLong::class, "class test.root.IcLong")
    check(IcAny::class, "class test.root.IcAny")
    check(IcOverIc::class, "class test.root.IcOverIc")
    check(UInt::class, "class kotlin.UInt")

    return "OK"
}
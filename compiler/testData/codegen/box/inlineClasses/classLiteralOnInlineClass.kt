// WITH_REFLECT
// TARGET_BACKEND: JVM
// IGNORE_BACKEND: JVM_IR

package root

import kotlin.reflect.KClass

inline class IcInt(val x: Int)
inline class IcLong(val l: Long)
inline class IcAny(val a: Any?)
inline class IcOverIc(val o: IcLong)

private const val testPackagePrefix = ""

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

    check(i::class, "class ${testPackagePrefix}root.IcInt")
    check(l::class, "class ${testPackagePrefix}root.IcLong")
    check(a::class, "class ${testPackagePrefix}root.IcAny")
    check(o::class, "class ${testPackagePrefix}root.IcOverIc")
    check(1u::class, "class kotlin.UInt")

    check(i::class.simpleName, "IcInt")
    check(l::class.simpleName, "IcLong")
    check(a::class.simpleName, "IcAny")
    check(o::class.simpleName, "IcOverIc")
    check(1u::class.simpleName, "UInt")

    reifiedCheck<IcInt>("class ${testPackagePrefix}root.IcInt", "IcInt")
    reifiedCheck<IcLong>("class ${testPackagePrefix}root.IcLong", "IcLong")
    reifiedCheck<IcAny>("class ${testPackagePrefix}root.IcAny", "IcAny")
    reifiedCheck<IcOverIc>("class ${testPackagePrefix}root.IcOverIc", "IcOverIc")
    reifiedCheck<UInt>("class kotlin.UInt", "UInt")

    val arrI = arrayOf(i)
    check(arrI[0]::class, "class ${testPackagePrefix}root.IcInt")

    val arrL = arrayOf(l)
    check(arrL[0]::class, "class ${testPackagePrefix}root.IcLong")

    val arrA = arrayOf(a)
    check(arrA[0]::class, "class ${testPackagePrefix}root.IcAny")

    val arrO = arrayOf(o)
    check(arrO[0]::class, "class ${testPackagePrefix}root.IcOverIc")

    val arrU = arrayOf(1u)
    check(arrU[0]::class, "class kotlin.UInt")

    check(IcInt::class, "class ${testPackagePrefix}root.IcInt")
    check(IcLong::class, "class ${testPackagePrefix}root.IcLong")
    check(IcAny::class, "class ${testPackagePrefix}root.IcAny")
    check(IcOverIc::class, "class ${testPackagePrefix}root.IcOverIc")
    check(UInt::class, "class kotlin.UInt")

    return "OK"
}
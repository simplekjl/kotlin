// WITH_RUNTIME
// TARGET_BACKEND: JVM
// IGNORE_BACKEND: JVM_IR

package root

inline class IcInt(val x: Int)
inline class IcLong(val l: Long)
inline class IcAny(val a: Any?)
inline class IcOverIc(val o: IcLong)

private const val testPackagePrefix = ""

fun check(c: Class<*>, s: String) {
    if (c.toString() != s) error("Fail, expected: $s, actual: $c")
}

inline fun <reified T> reifiedCheck(asString: String) {
    check(T::class.java, asString)
}

fun box(): String {
    val i = IcInt(0)
    val l = IcLong(0)
    val a = IcAny("foo")
    val o = IcOverIc(IcLong(0))

    check(i.javaClass, "class ${testPackagePrefix}root.IcInt")
    check(l.javaClass, "class ${testPackagePrefix}root.IcLong")
    check(a.javaClass, "class ${testPackagePrefix}root.IcAny")
    check(o.javaClass, "class ${testPackagePrefix}root.IcOverIc")
    check(1u.javaClass, "class kotlin.UInt")

    check(i::class.java, "class ${testPackagePrefix}root.IcInt")
    check(l::class.java, "class ${testPackagePrefix}root.IcLong")
    check(a::class.java, "class ${testPackagePrefix}root.IcAny")
    check(o::class.java, "class ${testPackagePrefix}root.IcOverIc")
    check(1u::class.java, "class kotlin.UInt")

    reifiedCheck<IcInt>("class ${testPackagePrefix}root.IcInt")
    reifiedCheck<IcLong>("class ${testPackagePrefix}root.IcLong")
    reifiedCheck<IcAny>("class ${testPackagePrefix}root.IcAny")
    reifiedCheck<IcOverIc>("class ${testPackagePrefix}root.IcOverIc")
    reifiedCheck<UInt>("class kotlin.UInt")

    val arrI = arrayOf(i)
    check(arrI[0].javaClass, "class ${testPackagePrefix}root.IcInt")

    val arrL = arrayOf(l)
    check(arrL[0].javaClass, "class ${testPackagePrefix}root.IcLong")

    val arrA = arrayOf(a)
    check(arrA[0].javaClass, "class ${testPackagePrefix}root.IcAny")

    val arrO = arrayOf(o)
    check(arrO[0].javaClass, "class ${testPackagePrefix}root.IcOverIc")

    val arrU = arrayOf(1u)
    check(arrU[0].javaClass, "class kotlin.UInt")

    return "OK"
}
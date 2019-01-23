// WITH_RUNTIME
// TARGET_BACKEND: JVM
// IGNORE_BACKEND: JVM_IR

package test.root

inline class IcInt(val x: Int)
inline class IcLong(val l: Long)
inline class IcAny(val a: Any?)
inline class IcOverIc(val o: IcLong)

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

    check(i.javaClass, "class test.root.IcInt")
    check(l.javaClass, "class test.root.IcLong")
    check(a.javaClass, "class test.root.IcAny")
    check(o.javaClass, "class test.root.IcOverIc")
    check(1u.javaClass, "class kotlin.UInt")

    check(i::class.java, "class test.root.IcInt")
    check(l::class.java, "class test.root.IcLong")
    check(a::class.java, "class test.root.IcAny")
    check(o::class.java, "class test.root.IcOverIc")
    check(1u::class.java, "class kotlin.UInt")

    reifiedCheck<IcInt>("class test.root.IcInt")
    reifiedCheck<IcLong>("class test.root.IcLong")
    reifiedCheck<IcAny>("class test.root.IcAny")
    reifiedCheck<IcOverIc>("class test.root.IcOverIc")
    reifiedCheck<UInt>("class kotlin.UInt")

    val arrI = arrayOf(i)
    check(arrI[0].javaClass, "class test.root.IcInt")

    val arrL = arrayOf(l)
    check(arrL[0].javaClass, "class test.root.IcLong")

    val arrA = arrayOf(a)
    check(arrA[0].javaClass, "class test.root.IcAny")

    val arrO = arrayOf(o)
    check(arrO[0].javaClass, "class test.root.IcOverIc")

    val arrU = arrayOf(1u)
    check(arrU[0].javaClass, "class kotlin.UInt")

    return "OK"
}
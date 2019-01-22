// IGNORE_BACKEND: JVM_IR
// TARGET_BACKEND: JVM

// WITH_REFLECT

class B<M>

private const val testPackagePrefix = ""

interface A<T, Y : B<T>> {

    fun <T, L> p(p: T): T {
        return p
    }

    val <T> T.z : T?
        get() = null
}


fun box(): String {
    val defaultImpls = Class.forName("${testPackagePrefix}A\$DefaultImpls")
    val declaredMethod = defaultImpls.getDeclaredMethod("p", A::class.java, Any::class.java)
    if (declaredMethod.toGenericString() != "public static <T_I1,Y,T,L> T ${testPackagePrefix}A\$DefaultImpls.p(${testPackagePrefix}A<T_I1, Y>,T)") return "fail 1: ${declaredMethod.toGenericString()}"

    val declaredProperty = defaultImpls.getDeclaredMethod("getZ", A::class.java, Any::class.java)
    if (declaredProperty.toGenericString() != "public static <T_I1,Y,T> T ${testPackagePrefix}A\$DefaultImpls.getZ(${testPackagePrefix}A<T_I1, Y>,T)") return "fail 2: ${declaredProperty.toGenericString()}"

    return "OK"
}

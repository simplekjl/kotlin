// IGNORE_BACKEND: NATIVE
// WITH_REFLECT

private const val testPackagePrefix = ""

class A

fun box(): String {
    val klass = A::class
    return if (klass.toString() == "class ${testPackagePrefix}A") "OK" else "Fail: $klass"
}

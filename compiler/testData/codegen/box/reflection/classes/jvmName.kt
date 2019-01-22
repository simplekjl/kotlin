// TARGET_BACKEND: JVM

// WITH_REFLECT

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.reflect.jvm.jvmName

private const val testPackagePrefix = ""

class Klass {
    class Nested
    companion object
}

fun box(): String {
    assertEquals("${testPackagePrefix}Klass", Klass::class.jvmName)
    assertEquals("${testPackagePrefix}Klass\$Nested", Klass.Nested::class.jvmName)
    assertEquals("${testPackagePrefix}Klass\$Companion", Klass.Companion::class.jvmName)

    class Local
    val l = Local::class.jvmName
    assertTrue(l != null && l.startsWith("${testPackagePrefix}JvmNameKt\$") && "\$box\$" in l && l.endsWith("\$Local"))

    val obj = object {}
    val o = obj.javaClass.kotlin.jvmName
    assertTrue(o != null && o.startsWith("${testPackagePrefix}JvmNameKt\$") && "\$box\$" in o && o.endsWith("\$1"))

    return "OK"
}

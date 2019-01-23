/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package test.collections

import java.lang.IllegalArgumentException
import kotlin.test.*

class UArrayJVMTest {
    @Test
    fun binarySearch() {
        val ubyte = ubyteArrayOf(1u, 0u, 4u, 5u, 8u, 12u, 2u, 3u, 7u, 7u, 7u, 9u, 2u)

        assertThrows(IllegalArgumentException::class.java) {
            ubyte.binarySearch(5u, 6, 1)
        }
        assertThrows(ArrayIndexOutOfBoundsException::class.java) {
            ubyte.binarySearch(5u, 6, 15)
        }
        assertThrows(ArrayIndexOutOfBoundsException::class.java) {
            ubyte.binarySearch(5u, -6, 7)
        }

        assertEquals(1, ubyte.binarySearch(0u, 1, 6))
        assertEquals(5, ubyte.binarySearch(12u, 1, 6))
        assertEquals(4, ubyte.binarySearch(8u, 1, 6))
        assertEquals(2, ubyte.binarySearch(4u, 1, 6))
        assertEquals(3, ubyte.binarySearch(5u, 2, 6))
        assertEquals(4, ubyte.binarySearch(8u, 2, 5))
        assertEquals(3, ubyte.binarySearch(5u, 3, 4))

        assertEquals(-4, ubyte.binarySearch(5u, 3, 3))
        assertEquals(-5, ubyte.binarySearch(7u, 1, 6))
        assertEquals(-12, ubyte.binarySearch(8u, 6, 12))
        assertEquals(-9, ubyte.binarySearch(5u, 6, 12))

        assertTrue(ubyte.binarySearch(7u, 6, 12) in 8..10)
    }
}

private fun assertThrows(exceptionClass: Class<*>, body: () -> Unit) {
    try {
        body()
        fail("Expecting an exception of type ${exceptionClass.name}")
    } catch (e: Throwable) {
        if (!exceptionClass.isAssignableFrom(e.javaClass)) {
            fail("Expecting an exception of type ${exceptionClass.name} but got ${e.javaClass.name}")
        }
    }
}

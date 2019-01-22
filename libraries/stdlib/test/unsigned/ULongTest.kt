/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package test.unsigned

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.random.Random
import kotlin.test.*

class ULongTest {

    private fun identity(u: ULong): ULong =
        (u.toLong() + 0).toULong()

    val zero = 0uL
    val one = 1uL
    val max = ULong.MAX_VALUE

    @Test
    fun equality() {

        fun testEqual(uv1: ULong, uv2: ULong) {
            assertEquals(uv1, uv2, "Boxed values should be equal")
            assertTrue(uv1.equals(uv2), "Boxed values should be equal: $uv1, $uv2")
            assertTrue(uv1 == uv2, "Values should be equal: $uv1, $uv2")
            assertEquals(uv1.hashCode(), uv2.hashCode())
            assertEquals((uv1 as Any).hashCode(), (uv2 as Any).hashCode())
            assertEquals(uv1.toString(), uv2.toString())
            assertEquals((uv1 as Any).toString(), (uv2 as Any).toString())
        }

        testEqual(one, identity(one))
        testEqual(max, identity(max))

        fun testNotEqual(uv1: ULong, uv2: ULong) {
            assertNotEquals(uv1, uv2, "Boxed values should be equal")
            assertTrue(uv1 != uv2, "Values should be not equal: $uv1, $uv2")
            assertNotEquals(uv1.toString(), uv2.toString())
            assertNotEquals((uv1 as Any).toString(), (uv2 as Any).toString())
        }

        testNotEqual(one, zero)
        testNotEqual(max, zero)
    }

    @Test
    fun convertToString() {
        fun testToString(expected: String, u: ULong) {
            assertEquals(expected, u.toString())
            assertEquals(expected, (u as Any).toString(), "Boxed toString")
            assertEquals(expected, "$u", "String template")
        }

        repeat(100) {
            val v = Random.nextLong() ushr 1
            testToString(v.toString(), v.toULong())
        }

        repeat(100) {
            val v = Random.nextLong(8446744073709551615L + 1)
            testToString("1${v.toString().padStart(19, '0')}", (5000000000000000000.toULong() * 2.toULong() + v.toULong()))
        }

        testToString("18446744073709551615", ULong.MAX_VALUE)
    }

    @Test
    fun comparisons() {
        fun <T> compare(op1: Comparable<T>, op2: T) = op1.compareTo(op2)

        fun testComparison(uv1: ULong, uv2: ULong, expected: Int) {
            val desc = "${uv1.toString()}, ${uv2.toString()}"
            assertEquals(expected, uv1.compareTo(uv2).sign, "compareTo: $desc")
            assertEquals(expected, (uv1 as Comparable<ULong>).compareTo(uv2).sign, "Comparable.compareTo: $desc")
            assertEquals(expected, compare(uv1, uv2).sign, "Generic compareTo: $desc")

            assertEquals(expected < 0, uv1 < uv2)
            assertEquals(expected <= 0, uv1 <= uv2)
            assertEquals(expected > 0, uv1 > uv2)
            assertEquals(expected >= 0, uv1 >= uv2)
        }

        fun testEquals(uv1: ULong, uv2: ULong) = testComparison(uv1, uv2, 0)
        fun testCompare(uv1: ULong, uv2: ULong, expected12: Int) {
            testComparison(uv1, uv2, expected12)
            testComparison(uv2, uv1, -expected12)
        }

        testEquals(one, identity(one))
        testEquals(max, identity(max))

        testCompare(zero, one, -1)
        testCompare(Long.MAX_VALUE.toULong(), zero, 1)

        testCompare(zero, ULong.MAX_VALUE, -1)
        testCompare((Long.MAX_VALUE).toULong() + one, ULong.MAX_VALUE, -1)
    }


    @Test
    fun convertToDouble() {
        assertEquals(0.0, zero.toDouble())
        assertEquals(1.0, one.toDouble())

        fun testEpsEquals(v1: Double, v2: ULong) {
            val eps = 1e+4
            assertTrue(abs(v1 - v2.toDouble()) < eps)
        }

        testEpsEquals(2.0.pow(ULong.SIZE_BITS) - 1, max)
        testEpsEquals(2.0 * Long.MAX_VALUE + 1, max)

        repeat(100) {
            val rand = Random.nextLong(from = 0, until = Long.MAX_VALUE)
            val d = rand.toDouble()
            val ul = rand.toULong()
            testEpsEquals(d, ul)
        }

        repeat(100) {
            val rand = Random.nextLong(from = 0, until = Long.MAX_VALUE)
            val d = Long.MAX_VALUE.toDouble() + rand.toDouble()
            val ul = Long.MAX_VALUE.toULong() + rand.toULong()
            testEpsEquals(d, ul)
        }
    }

    @Test
    fun convertDoubleToULong() {
        fun testEquals(v1: Double, v2: ULong) = assertEquals(v1.toULong(), v2)

        testEquals(0.0, zero)
        testEquals(-1.0, zero)

        testEquals(-2_000_000_000_000.0, zero)
        testEquals((-0xFFFF_FFFF_FFFF).toDouble(), zero)
        testEquals(Double.MIN_VALUE, zero)
        testEquals(Double.NEGATIVE_INFINITY, zero)
        testEquals(Double.NaN, zero)

        testEquals(1.0, one)

        testEquals(2_000_000_000_000_000_000_000.0, max)
        testEquals(2.0.pow(ULong.SIZE_BITS), max)
        testEquals(2.0.pow(ULong.SIZE_BITS + 5), max)
        testEquals(Double.MAX_VALUE, max)
        testEquals(Double.POSITIVE_INFINITY, max)

        repeat(100) {
            val v = -Random.nextDouble() * 0xFFFF_FFFF_FFFF * 0xFFFF_FF
            testEquals(v, zero)
        }

        repeat(100) {
            val v = (1.0 + Random.nextDouble()) * 0xFFFF_FFFF_FFFF * 0xFFFF_FF
            testEquals(v, max)
        }

        repeat(100) {
            val v = Random.nextDouble() * Long.MAX_VALUE
            testEquals(v, v.toLong().toULong())
        }

        repeat(100) {
            val diff = Random.nextDouble() * Long.MAX_VALUE
            val d = Long.MAX_VALUE.toDouble() + diff
            val ul = Long.MAX_VALUE.toULong() + diff.toLong().toULong()
            val eps = 1e-6

            assertTrue(d.toULong() / ul <= 1u)
            assertTrue(d / ul.toDouble() < 1 + eps)
            assertTrue(d / ul.toDouble() > 1 - eps)
        }
    }
}
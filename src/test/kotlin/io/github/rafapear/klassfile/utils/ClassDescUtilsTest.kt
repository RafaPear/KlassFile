package io.github.rafapear.klassfile.utils

import kotlin.reflect.full.createType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClassDescUtilsTest {

    @Test
    fun `Test classDesc conversions from name Class KClass and KType`() {
        assertEquals(
            "Ljava/lang/String;",
            classDesc("java.lang.String").descriptorString()
        )

        assertEquals(
            "Ljava/lang/String;",
            classDesc(String::class.java).descriptorString()
        )

        assertEquals(
            "I",
            classDesc(Int::class).descriptorString()
        )

        assertEquals(
            "I",
            classDesc(Int::class.createType()).descriptorString()
        )

        assertEquals(
            "Ljava/lang/Integer;",
            classDesc(Int::class.createType(nullable = true)).descriptorString()
        )

        assertEquals(
            "Ljava/lang/String;",
            classDesc(String::class.createType(nullable = true)).descriptorString()
        )
    }

    @Test
    fun `Test nullableClassDesc boxes primitives and leaves references unchanged`() {
        assertEquals(
            "Ljava/lang/Integer;",
            nullableClassDesc(Int::class).descriptorString()
        )

        assertEquals(
            "Ljava/lang/String;",
            nullableClassDesc(String::class).descriptorString()
        )

        assertEquals(
            "Ljava/lang/Void;",
            nullableClassDesc(Unit::class).descriptorString()
        )
    }

    @Test
    fun `Test klassDescOf handles reference primitive and array types`() {
        assertEquals(
            "Ljava/lang/String;",
            klassDescOf<String>().classDesc.descriptorString()
        )

        assertEquals(
            "[I",
            klassDescOf<IntArray>().classDesc.descriptorString()
        )

        assertEquals(
            "[Ljava/lang/String;",
            klassDescOf<Array<String>>().classDesc.descriptorString()
        )
    }

    @Test
    fun `Test klassDescOf rejects arrays of primitive wrappers`() {
        assertFailsWith<UnsupportedKotlinArrayOfPrimitivesError> {
            klassDescOf<Array<Int>>()
        }
    }
}



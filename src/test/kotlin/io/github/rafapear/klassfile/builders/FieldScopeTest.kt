package io.github.rafapear.klassfile.builders

import io.github.rafapear.klassfile.models.OwnerRef
import io.github.rafapear.klassfile.utils.klassDescOf
import java.lang.classfile.ClassFile.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldScopeTest {

    private val ownerRef = OwnerRef(klassDescOf<String>(), klassDescOf<String>())

    @Test
    fun `defining the same field in multiple ways returns a similar reference`() {
        val scope = FieldScope(ownerRef)

        val field1 = scope.defineField<Int>("field1")
        val field2 by scope.field<Int>()
        val field3 = scope.defineField("field3", klassDescOf<Int>())

        val list = listOf(field1, field2, field3)

        assertTrue {
            list.all {
                it.type == field1.type && it.owner == field1.owner && it.flags == field1.flags
            }
        }
    }

    @Test
    fun `comparing different type fields returns false`() {
        val scope = FieldScope(ownerRef)

        val field1 = scope.defineField<Int>("field1")
        val field2 by scope.field<Float>()
        val field3 = scope.defineField<Unit>("field3")

        val list = listOf(field1, field2, field3)

        assertFalse {
            list.all { it.type == field1.type && it.owner == field1.owner && it.flags == field1.flags }
        }
    }

    @Test
    fun `field creates reference`() {
        val scope = FieldScope(ownerRef)

        val field = scope.defineField<Int>("count")

        assertEquals("count", field.name)
        assertEquals(ownerRef.thisClass, field.owner)
        assertEquals(klassDescOf<Int>(), field.type)
    }

    @Test
    fun `field is private by default`() {
        val scope = FieldScope(ownerRef)

        val field = scope.defineField<Int>("count")

        assertEquals(ACC_PRIVATE, field.flags)
    }

    @Test
    fun `field supports custom flags`() {
        val scope = FieldScope(ownerRef)

        val field = scope.defineField<Int>("count") {
            public()
            static()
            final()
        }

        assertEquals(
            ACC_PUBLIC or ACC_STATIC or ACC_FINAL,
            field.flags
        )
    }

    @Test
    fun `delegated field uses property name`() {
        val scope = FieldScope(ownerRef)

        val count by scope.field<Int>()

        assertEquals("count", count.name)
        assertEquals(ownerRef.thisClass, count.owner)
        assertEquals(klassDescOf<Int>(), count.type)
    }
}
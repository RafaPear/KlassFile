package io.github.rafapear.klassfile.builders

import io.github.rafapear.klassfile.models.OwnerRef
import io.github.rafapear.klassfile.utils.klassDescOf
import java.lang.classfile.ClassFile.*
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldScopeTest {

    private val ownerRef = OwnerRef(klassDescOf<String>(), klassDescOf<String>())

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
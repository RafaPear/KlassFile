package io.github.rafapear.klassfile.models

import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KlassDescTest {

    private data class ExampleObject(
        val name: String = "Rafa",
        val age: Int = 21,
    )

    inline fun <reified T : Any> kTypeOf(): KType =
        T::class.createType()

    private fun assertDescriptor(
        expected: String,
        klassDesc: KlassDesc<*>,
    ) {
        assertEquals(expected, klassDesc.classDesc.descriptorString())
    }

    // =========================================================================
    // KType constructor
    // =========================================================================

    @Test
    fun `Test constructor with KType maps reference primitive Unit and arrays`() {
        val stringDesc = KlassDesc<String>(kTypeOf<String>())
        assertDescriptor("Ljava/lang/String;", stringDesc)
        assertEquals(String::class, stringDesc.kClass)

        val intDesc = KlassDesc<Int>(kTypeOf<Int>())
        assertDescriptor("I", intDesc)
        assertEquals(Int::class, intDesc.kClass)

        val unitDesc = KlassDesc<Unit>(kTypeOf<Unit>())
        assertDescriptor("V", unitDesc)
        assertEquals(Unit::class, unitDesc.kClass)

        val exampleDesc = KlassDesc<ExampleObject>(kTypeOf<ExampleObject>())
        assertDescriptor(
            $$"Lio/github/rafapear/klassfile/models/KlassDescTest$ExampleObject;",
            exampleDesc,
        )
        assertEquals(ExampleObject::class, exampleDesc.kClass)

        val primitiveArrayDesc = KlassDesc<IntArray>(kTypeOf<IntArray>())
        assertDescriptor("[I", primitiveArrayDesc)
        assertEquals(IntArray::class, primitiveArrayDesc.kClass)

        val arrayType = Array<Int>::class.createType(
            listOf(KTypeProjection.invariant(Int::class.createType()))
        )
        val objectArrayDesc = KlassDesc<Array<Int>>(arrayType)
        assertDescriptor("[Ljava/lang/Integer;", objectArrayDesc)
        assertEquals(Array<Int>::class, objectArrayDesc.kClass)
    }

    @Test
    fun `Test constructor with KType handles nested arrays`() {
        val stringType = String::class.createType()

        val stringArrayType = Array<String>::class.createType(
            listOf(
                KTypeProjection.invariant(stringType)
            )
        )

        val nestedArrayType = Array<Array<String>>::class.createType(
            listOf(
                KTypeProjection.invariant(stringArrayType)
            )
        )

        val klassDesc = KlassDesc<Array<Array<String>>>(
            nestedArrayType
        )

        assertEquals(
            "[[Ljava/lang/String;",
            klassDesc.classDesc.descriptorString()
        )
    }

    // =========================================================================
    // KType constructor - boxing
    // =========================================================================

    @Test
    fun `Test boxed constructor boxes primitives but not references or arrays`() {
        assertDescriptor(
            "Ljava/lang/Integer;",
            KlassDesc<Int>(kTypeOf<Int>(), boxed = true)
        )

        assertDescriptor(
            "Ljava/lang/String;",
            KlassDesc<String>(kTypeOf<String>(), boxed = true)
        )

        assertDescriptor(
            "[I",
            KlassDesc<IntArray>(kTypeOf<IntArray>(), boxed = true)
        )
    }

    @Test
    fun `Test nullable KType constructor boxes nullable primitives`() {
        val nullableIntType = Int::class.createType(nullable = true)
        val nullableStringType = String::class.createType(nullable = true)

        assertDescriptor(
            "Ljava/lang/Integer;",
            KlassDesc<Int>(nullableIntType)
        )

        assertDescriptor(
            "Ljava/lang/String;",
            KlassDesc<String>(nullableStringType)
        )
    }

    // =========================================================================
    // KClass constructor
    // =========================================================================

    @Test
    fun `Test KClass constructor maps reference primitive array and Unit`() {
        val stringDesc = KlassDesc(String::class)
        assertDescriptor("Ljava/lang/String;", stringDesc)
        assertEquals(String::class, stringDesc.kClass)

        val intDesc = KlassDesc(Int::class)
        assertDescriptor("I", intDesc)
        assertEquals(Int::class, intDesc.kClass)

        val arrayDesc = KlassDesc(IntArray::class)
        assertDescriptor("[I", arrayDesc)
        assertEquals(IntArray::class, arrayDesc.kClass)

        val unitDesc = KlassDesc(Unit::class)
        assertDescriptor("V", unitDesc)
        assertEquals(Unit::class, unitDesc.kClass)
    }

    // =========================================================================
    // Java Class constructor
    // =========================================================================

    @Test
    fun `Test Java Class constructor maps primitive boxed primitive and arrays`() {
        val stringDesc = KlassDesc<String>(String::class.java)
        assertDescriptor("Ljava/lang/String;", stringDesc)
        assertEquals(String::class, stringDesc.kClass)

        val primitiveDesc = KlassDesc<Int>(Int::class.javaPrimitiveType!!)
        assertDescriptor("I", primitiveDesc)
        assertEquals(Int::class, primitiveDesc.kClass)

        val boxedDesc = KlassDesc<Int>(Int::class.javaObjectType)
        assertDescriptor("I", boxedDesc)
        assertEquals(Int::class, boxedDesc.kClass)

        val primitiveArrayDesc = KlassDesc<IntArray>(IntArray::class.java)
        assertDescriptor("[I", primitiveArrayDesc)
        assertEquals(IntArray::class, primitiveArrayDesc.kClass)

        val objectArrayDesc = KlassDesc<Array<String>>(Array<String>::class.java)
        assertDescriptor("[Ljava/lang/String;", objectArrayDesc)
        assertEquals(Array<String>::class, objectArrayDesc.kClass)
    }

    // =========================================================================
    // array()
    // =========================================================================

    @Test
    fun `Test array() builds object primitive and nested arrays`() {
        val stringDesc = KlassDesc(String::class)
        val stringArray = stringDesc.array()
        assertDescriptor("[Ljava/lang/String;", stringArray)
        assertEquals(String::class, stringArray.elementType.kClass)
        assertEquals(stringDesc, stringArray.elementType)

        val intDesc = KlassDesc(Int::class)
        val intArray = intDesc.array()
        assertDescriptor("[I", intArray)
        assertEquals(Int::class, intArray.elementType.kClass)
        assertEquals(intDesc, intArray.elementType)

        assertDescriptor("[[Ljava/lang/String;", stringDesc.array().array())
        assertDescriptor("[[[I", intDesc.array().array().array())
    }

    // =========================================================================
    // equals / hashCode
    // =========================================================================

    @Test
    fun `Test equality and hashing use the JVM descriptor`() {
        val stringFromKClass = KlassDesc(String::class)
        val stringFromJava = KlassDesc<String>(String::class.java)
        val intDesc = KlassDesc(Int::class)
        val boxedIntDesc = KlassDesc<Int>(kTypeOf<Int>(), boxed = true)
        val stringArray = KlassDesc(String::class).array()
        val anotherStringArray = KlassDesc(String::class).array()
        val intArray = KlassDesc(Int::class).array()

        assertEquals(stringFromKClass, stringFromJava)
        assertEquals(stringFromKClass.hashCode(), stringFromJava.hashCode())
        assertFalse(stringFromKClass == intDesc)
        assertNotEquals(intDesc, boxedIntDesc)
        assertFalse(stringArray == intArray)
        assertEquals(stringArray, anotherStringArray)
        assertEquals(stringArray.hashCode(), anotherStringArray.hashCode())
    }

    // =========================================================================
    // toString()
    // =========================================================================

    @Test
    fun `Test toString returns readable Java names`() {
        assertEquals("String", KlassDesc(String::class).toString())
        assertEquals("int", KlassDesc(Int::class).toString())
        assertEquals(
            "Integer",
            KlassDesc<Int>(kTypeOf<Int>(), boxed = true).toString()
        )
    }

    // =========================================================================
    // isAssignableFrom()
    // =========================================================================

    @Test
    fun `Test isAssignableFrom follows JVM rules for reference primitive and array types`() {
        val any = KlassDesc(Any::class)
        val string = KlassDesc(String::class)
        val example = KlassDesc(ExampleObject::class)
        val number = KlassDesc(Number::class)
        val integer = KlassDesc<Int>(kTypeOf<Int>(), boxed = true)
        val primitiveInt = KlassDesc(Int::class)
        val stringArray = KlassDesc(String::class).array()
        val anotherStringArray = KlassDesc(String::class).array()
        val anyArray = KlassDesc(Any::class).array()
        val twoDimAnyArray = anyArray.array()
        val twoDimStringArray = stringArray.array()
        val primitiveArray = KlassDesc(Int::class).array()
        val longArray = KlassDesc(Long::class).array()

        assertTrue(any.isAssignableFrom(string))
        assertTrue(any.isAssignableFrom(example))
        assertTrue(any.isAssignableFrom(any))
        assertTrue(number.isAssignableFrom(integer))
        assertTrue(string.isAssignableFrom(string))

        assertFalse(string.isAssignableFrom(number))
        assertFalse(primitiveInt.isAssignableFrom(integer))
        assertFalse(integer.isAssignableFrom(primitiveInt))

        assertTrue(anyArray.isAssignableFrom(stringArray))
        assertTrue(stringArray.isAssignableFrom(anyArray))
        assertTrue(twoDimAnyArray.isAssignableFrom(twoDimStringArray))
        assertTrue(stringArray.isAssignableFrom(anotherStringArray))
        assertTrue(primitiveArray.isAssignableFrom(longArray))
        assertTrue(primitiveArray.isAssignableFrom(stringArray))
        assertTrue(stringArray.isAssignableFrom(primitiveArray))
    }
}
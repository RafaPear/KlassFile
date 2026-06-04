package pt.rafap.klassfile

import pt.rafap.klassfile.models.MethodRef
import pt.rafap.klassfile.utils.classDesc
import java.io.Serializable
import java.lang.classfile.ClassFile.*
import java.lang.constant.ClassDesc
import java.lang.constant.ConstantDescs.*
import java.lang.reflect.Modifier
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("unused")
interface HierarchyGreeter {
	fun greet(): String
}

@Suppress("unused")
abstract class HierarchyBaseGreeter {
	abstract fun greet(): String
}

class KlassFileBuilderTests {

	private fun uniqueClassName(prefix: String): String =
		"pt.isel.dsl.Generated${prefix}_${UUID.randomUUID().toString().replace("-", "")}"

	@Test
	fun `classDesc overloads produce matching descriptors from String`() {

		lateinit var descriptors: List<ClassDesc>
		KlassFileBuilder.klass(uniqueClassName("ClassDescString")) {
			descriptors = listOf(
				classDesc("java.lang.String"),
				classDesc(String::class.java),
				classDesc(String::class),
				classDesc<String>()
			)
		}

		descriptors.forEach {
			assertEquals(CD_String, it)
		}

		assertEquals(descriptors.distinct().size, 1)
	}

	@Test
	fun `classDesc overloads produce matching descriptors from primitive`() {

		val primitives = listOf(
			Triple(CD_byte, Byte::class.java, Byte::class),
			Triple(CD_short, Short::class.java, Short::class),
			Triple(CD_int, Int::class.java, Int::class),
			Triple(CD_long, Long::class.java, Long::class),
			Triple(CD_float, Float::class.java, Float::class),
			Triple(CD_double, Double::class.java, Double::class),
			Triple(CD_char, Char::class.java, Char::class),
			Triple(CD_boolean, Boolean::class.java, Boolean::class),
		)

		KlassFileBuilder.klass(uniqueClassName("ClassDescPrimitive")) {

			primitives.forEach { (expected, javaType, kType) ->

				val descriptors = buildList {
					add(classDesc(javaType))
					add(classDesc(kType))

					add(
						when (kType) {
							Byte::class -> classDesc<Byte>()
							Short::class -> classDesc<Short>()
							Int::class -> classDesc<Int>()
							Long::class -> classDesc<Long>()
							Float::class -> classDesc<Float>()
							Double::class -> classDesc<Double>()
							Char::class -> classDesc<Char>()
							Boolean::class -> classDesc<Boolean>()
							else -> error("Unsupported primitive")
						}
					)
				}

				descriptors.forEach {
					assertEquals(expected, it)
				}

				assertEquals(1, descriptors.distinct().size)
			}
		}
	}

	@Test
	fun `field overloads emit correct field metadata`() {

		val generated = KlassFileBuilder.klass(uniqueClassName("Fields")) {
			field<String>("explicit", CD_String)
			field<Boolean>("genericFlag", ACC_PROTECTED)
			publicField<Int>("publicCounter")
			staticField<Long>("staticVersion")
		}

		val clazz = generated.writeAndGetClass<Any>().java

		with(clazz.getDeclaredField("explicit")) {
			assertEquals(String::class.java, type)
			assertTrue(Modifier.isPrivate(modifiers))
		}

		with(clazz.getDeclaredField("genericFlag")) {
			assertEquals(Boolean::class.javaPrimitiveType, type)
			assertTrue(Modifier.isProtected(modifiers))
		}

		with(clazz.getDeclaredField("publicCounter")) {
			assertEquals(Int::class.javaPrimitiveType, type)
			assertTrue(Modifier.isPublic(modifiers))
		}

		with(clazz.getDeclaredField("staticVersion")) {
			assertEquals(Long::class.javaPrimitiveType, type)
			assertTrue(Modifier.isStatic(modifiers))
		}
	}

	@Test
	fun `hierarchy helpers use the generic overloads and reject concrete classes`() {

		val implementations = listOf(
			KlassFileBuilder.klass(uniqueClassName("Implements")) {
				flags(ACC_PUBLIC or ACC_ABSTRACT)
				implements<HierarchyGreeter>()

				implementMethod("greet", returnType = CD_String) {
					ldc("Hello")
					areturn()
				}
			}.writeAndGetClass<Any>().java,

			KlassFileBuilder.klass(uniqueClassName("InheritInterface")) {
				flags(ACC_PUBLIC or ACC_ABSTRACT)
				inherit<HierarchyGreeter>()

				implementMethod("greet", returnType = CD_String) {
					ldc("Hello")
					areturn()
				}
			}.writeAndGetClass<Any>().java
		)

		implementations.forEach {
			assertTrue(HierarchyGreeter::class.java.isAssignableFrom(it))
			assertEquals(Any::class.java, it.superclass)
		}

		val abstractExtensions = listOf(
			KlassFileBuilder.klass(uniqueClassName("Extends")) {
				flags(ACC_PUBLIC or ACC_ABSTRACT)
				extends<HierarchyBaseGreeter>()

				implementMethod("greet", returnType = CD_String) {
					ldc("Hello")
					areturn()
				}
			}.writeAndGetClass<Any>().java,

			KlassFileBuilder.klass(uniqueClassName("InheritAbstract")) {
				flags(ACC_PUBLIC or ACC_ABSTRACT)
				inherit<HierarchyBaseGreeter>()

				implementMethod("greet", returnType = CD_String) {
					ldc("Hello")
					areturn()
				}
			}.writeAndGetClass<Any>().java
		)

		abstractExtensions.forEach {
			assertEquals(HierarchyBaseGreeter::class.java.name, it.superclass?.name)
		}

		assertFailsWith<IllegalArgumentException> {
			KlassFileBuilder.klass(uniqueClassName("InheritConcrete")) {
				inherit<String>()
			}
		}
	}

	@Test
	fun `method builders create callable members and Klass wrapper works`() {

		lateinit var constructorRef: MethodRef
		lateinit var staticRef: MethodRef
		lateinit var privateRef: MethodRef
		lateinit var greetRef: MethodRef

		val generated = KlassFileBuilder.klass(uniqueClassName("Methods")) {

			val nameField = field<String>("name")

			constructorRef = constructor {
				defaultConstructor()
				putThisField(nameField, "Jerry")
				return_()
			}

			staticRef = staticMethod("prefix", returnType = CD_String) {
				ldc("Hello, ")
				areturn()
			}

			privateRef = privateMethod("suffix", returnType = CD_String) {
				ldc("!")
				areturn()
			}

			greetRef = method("greet", returnType = CD_String) {
				getThisField(nameField)
				areturn()
			}

			method("staticMessage", returnType = CD_String) {
				invokestatic(staticRef)
				areturn()
			}

			method("secretMessage", returnType = CD_String) {
				invokeMethod(privateRef)
				areturn()
			}

			main {
				return_()
			}
		}

		assertTrue(generated.bytes.isNotEmpty())

		assertTrue(constructorRef.isConstructor)
		assertEquals(INIT_NAME, constructorRef.name)

		assertTrue(staticRef.flags and ACC_STATIC != 0)
		assertTrue(privateRef.flags and ACC_PRIVATE != 0)

		assertEquals("greet", greetRef.name)

		val instance = generated.writeAndGetInstance<Any>()
		val clazz = instance.javaClass

		assertEquals("Jerry", clazz.getMethod("greet").invoke(instance))
		assertEquals("Hello, ", clazz.getMethod("staticMessage").invoke(instance))
		assertEquals("!", clazz.getMethod("secretMessage").invoke(instance))

		with(clazz.getDeclaredMethod("main", Array<String>::class.java)) {
			assertTrue(Modifier.isPublic(modifiers))
			assertTrue(Modifier.isStatic(modifiers))
		}
	}

	@Test
	fun `reflective invocation helpers and param field store`() {

		val generated = KlassFileBuilder.klass(uniqueClassName("Reflective")) {

			val nameField = field<String>("name")

			constructor {
				defaultConstructor()
				putThisField(nameField, "Alice")
				return_()
			}

			method("timeNow", returnType = CD_long) {
				invokestatic<System>("currentTimeMillis")
				lreturn()
			}

			method("hello", returnType = CD_String) {
				ldc("Hello ")
				getThisField(nameField)
				invokevirtual<String>("concat", classDesc<String>())
				areturn()
			}

			method("setName", args = arrayOf(CD_String)) {
				aload(0)
				aload(1)
				putfield(nameField)
				return_()
			}
		}

		val instance = generated.writeAndGetInstance<Any>()
		val clazz = instance.javaClass

		assertEquals("Hello Alice", clazz.getMethod("hello").invoke(instance))

		val time = clazz.getMethod("timeNow").invoke(instance) as Long
		assertTrue(time > 0)

		clazz.getMethod("setName", String::class.java)
			.invoke(instance, "Bob")

		assertEquals("Hello Bob", clazz.getMethod("hello").invoke(instance))
	}

	@Test
	fun `classDesc primitive branches and ldc exceptions`() {

		KlassFileBuilder.klass(uniqueClassName("PrimitiveBranches")) {

			listOf(
				Byte::class.java to CD_byte,
				Short::class.java to CD_short,
				Int::class.java to CD_int,
				Long::class.java to CD_long,
				Float::class.java to CD_float,
				Double::class.java to CD_double,
				Char::class.java to CD_char,
				Boolean::class.java to CD_boolean,
			).forEach { (type, expected) ->
				assertEquals(expected, classDesc(type))
			}

			method("failLdc") {
				assertFailsWith<IllegalArgumentException> {
					ldc(true)
				}

				return_()
			}
		}
	}

	@Test
	fun `whileLoop class initializer raw field access and ldc variants`() {

		val generated = KlassFileBuilder.klass(uniqueClassName("StaticAndLoop")) {

			val staticCounter = staticField<Int>("COUNTER")
			val instanceField = field<Long>("lval")

			constructor(ACC_STATIC) {
				ldc(42)
				putstatic(staticCounter)
				return_()
			}

			constructor {
				defaultConstructor()

				aload(0)
				ldc(10L)
				putfield(instanceField)

				return_()
			}

			staticMethod("getStaticCounter", returnType = CD_int) {
				getstatic(staticCounter)
				ireturn()
			}

			method("loopTest", returnType = CD_int) {

				iconst_0()
				istore(1)

				whileLoop(
					condition = { end ->
						iload(1)
						iconst_3()
						if_icmpge(end)
					},
					body = {
						iinc(1, 1)
					}
				)

				iload(1)
				ireturn()
			}

			method("ldcAndGetField", returnType = CD_long) {

				ldc(1.5f)
				pop()

				ldc(2.5)
				pop2()

				aload(0)
				getfield(instanceField)
				lreturn()
			}
		}

		val instance = generated.writeAndGetInstance<Any>()
		val clazz = instance.javaClass

		assertEquals(42, clazz.getMethod("getStaticCounter").invoke(null))
		assertEquals(3, clazz.getMethod("loopTest").invoke(instance))
		assertEquals(10L, clazz.getMethod("ldcAndGetField").invoke(instance))

		generated.writeToDisk()

		assertEquals(clazz, generated.load<Any>().java)
	}

	@Test
	fun `invokespecial non init getstatic reflection and explicit hierarchy descriptors`() {

		val generated = KlassFileBuilder.klass(uniqueClassName("SpecialReflect")) {

			extends(classDesc<HierarchyBaseGreeter>())
			implements<Serializable>()

			constructor {
				defaultConstructor()
				return_()
			}

			method("testToString", returnType = CD_String) {
				aload(0)
				invokespecial<Any>("toString")
				areturn()
			}

			method("printOut") {
				getstatic<System>("out")
				pop()
				return_()
			}

			method("greet", returnType = CD_String) {
				ldc("Hello")
				areturn()
			}
		}

		val instance = generated.writeAndGetInstance<Any>()
		val clazz = instance.javaClass

		val result = clazz.getMethod("testToString").invoke(instance) as String
		assertTrue(result.contains("SpecialReflect"))

		clazz.getMethod("printOut").invoke(instance)

		assertTrue(Serializable::class.java.isAssignableFrom(clazz))
	}
}
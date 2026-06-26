package pt.rafap.klassfile

import pt.rafap.klassfile.utils.EagerDelegate
import java.io.File
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import kotlin.reflect.KClass

private val path = Unit::class.java
    .getResource("/")
    ?.toURI()
    ?.path ?: (System.getProperty("user.dir") + "/")

/** Class loader used to load classes written by the DSL at runtime. */
val loader = URLClassLoader(arrayOf(File(path).toURI().toURL()))

/**
 * Writes the given bytecode to the workspace output path using the class binary name.
 *
 * @param className the binary class name.
 * @param bytes the generated class bytes.
 */
fun write(className: String, bytes: ByteArray) {
    File(
        path,
        className.replace('.', '/') + ".class"
    ).also { it.parentFile.mkdirs() } // Create directories if they do not exist
        .writeBytes(bytes)
}

@Suppress("UNCHECKED_CAST")
/**
 * Loads a generated class from the runtime class loader used by [write].
 *
 * @param className the binary class name to load.
 * @return the loaded Kotlin class.
 */
fun <T : Any> loadClass(className: String): KClass<T> {
    val clazz = loader.loadClass(className).kotlin as KClass<T>
    return clazz
}

/**
 * Writes the bytecode to disk and immediately loads the resulting class.
 *
 * @param className the binary class name.
 * @param bytes the generated class bytes.
 * @return the loaded Kotlin class.
 */
fun <T : Any> writeAndGetClass(
    className: String,
    bytes: ByteArray,
): KClass<T> {
    write(className, bytes)
    return loadClass(className)
}

/**
 * Converts JVM access flags into a stable list of human-readable modifier names.
 *
 * @param flags the raw JVM access flags.
 * @return the human-readable modifiers.
 */
fun toModifiers(flags: Int): List<String> = buildList {
    when {
        Modifier.isPublic(flags) -> add("public")
        Modifier.isProtected(flags) -> add("protected")
        Modifier.isPrivate(flags) -> add("private")
    }
    if (Modifier.isAbstract(flags)) add("abstract")
    if (Modifier.isStatic(flags)) add("static")
    if (Modifier.isFinal(flags)) add("final")
    if (Modifier.isNative(flags)) add("native")
}

inline fun <reified T: Any> klassFile(
    name: String,
    noinline block: KlassFileBuilder<T>.() -> Unit
) = KlassFileBuilder.klass<T>(name, block)

inline fun <reified T: Any> klassFile(
    noinline block: KlassFileBuilder<T>.() -> Unit
) = EagerDelegate { _, property -> KlassFileBuilder.klass<T>(property.name, block) }

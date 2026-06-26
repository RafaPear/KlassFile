package pt.rafap.klassfile

import java.io.File
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import kotlin.reflect.KClass

private val path = Unit::class.java
    .getResource("/")
    ?.toURI()
    ?.path ?: (System.getProperty("user.dir") + "/")

val loader = URLClassLoader(arrayOf(File(path).toURI().toURL()))

fun write(className: String, bytes: ByteArray) {
    File(
        path,
        className.replace('.', '/') + ".class"
    ).also { it.parentFile.mkdirs() } // Create directories if they do not exist
        .writeBytes(bytes)
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> loadClass(className: String): KClass<T> {
    val clazz = loader.loadClass(className).kotlin as KClass<T>
    return clazz
}

fun <T : Any> writeAndGetClass(
    className: String,
    bytes: ByteArray,
): KClass<T> {
    write(className, bytes)
    return loadClass(className)
}

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

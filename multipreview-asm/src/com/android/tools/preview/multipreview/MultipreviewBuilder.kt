package com.android.tools.preview.multipreview

import com.android.tools.preview.multipreview.visitors.MultipreviewClassVisitor
import java.util.zip.ZipFile
import org.objectweb.asm.ClassReader
import java.io.File
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.readBytes

/** Consumer interface processing class bytecode. */
fun interface ClassProcessor {

  fun onClassBytecode(classByteCode: ByteArray)
}

/** Consumer feeding class bytecode to the [processor]. */
fun interface ClassBytecodeProvider {

  fun forEachClass(processor: ClassProcessor)
}

/** Settings to build a [Multipreview] structure. */
data class MultipreviewSettings(
  val baseAnnotation: String,
  val parameterAnnotation: String
)

/** Builds multipreview structure based on the [settings] and bytecode data from [provider]. */
fun buildMultipreview(
  settings: MultipreviewSettings,
  provider: ClassBytecodeProvider
): Multipreview {
  val multipreviewGraph = Graph()
  provider.forEachClass {
    val cr = ClassReader(it)
    cr.accept(
      MultipreviewClassVisitor(settings, cr.className.classPathToName, multipreviewGraph),
      0
    )
  }
  multipreviewGraph.prune()
  return multipreviewGraph
}

private fun forEachClass(paths: Collection<String>, classProcessor: ClassProcessor) {
  paths.forEach { path ->
      if (path.endsWith(".jar")) {
          ZipFile(path).use { zipFile ->
              zipFile.stream().filter { it.name.endsWith(".class") }.forEach {
                  zipFile.getInputStream(it).use { stream ->
                      classProcessor.onClassBytecode(stream.readAllBytes())
                  }
              }
          }
      } else if (File(path).isDirectory) {
          File(path).walk().filter { it.name.endsWith(".class") }.forEach {
              classProcessor.onClassBytecode(it.readBytes())
          }
      }
  }
}

fun buildMultipreview(settings: MultipreviewSettings, paths: Collection<String>): Multipreview {
  return buildMultipreview(settings) { processor ->
    forEachClass(paths, processor::onClassBytecode)
  }
}

package com.android.tools.preview.multipreview

import com.android.tools.preview.multipreview.visitors.AnnotationClassVisitor
import com.android.tools.preview.multipreview.visitors.MethodsClassVisitor
import com.android.tools.preview.multipreview.visitors.MethodsFilter
import java.util.zip.ZipFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import java.io.File

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

/**
 * Builds multipreview structure based on the [settings] and bytecode data from [provider] for
 * methods allowed by [methodsFilter].
 */
fun buildMultipreview(
  settings: MultipreviewSettings,
  methodsFilter: MethodsFilter = MethodsFilter { true },
  provider: ClassBytecodeProvider,
): Multipreview {
  val multipreviewGraph = Graph()
  provider.forEachClass {
    val cr = ClassReader(it)
    val classVisitor = if ((cr.access and Opcodes.ACC_ANNOTATION) != 0) {
      val recorder = multipreviewGraph.addAnnotationNode(DerivedAnnotationRepresentation(cr.className.classPathToName))
      AnnotationClassVisitor(settings.baseAnnotation, recorder)
    } else {
      MethodsClassVisitor(settings, cr.className.classPathToName, multipreviewGraph, methodsFilter)
    }
    cr.accept(classVisitor, 0)
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

fun buildMultipreview(
  settings: MultipreviewSettings,
  paths: Collection<String>,
  methodsFilter: MethodsFilter = MethodsFilter { true }
): Multipreview {
  return buildMultipreview(settings, methodsFilter) { processor ->
    forEachClass(paths, processor::onClassBytecode)
  }
}

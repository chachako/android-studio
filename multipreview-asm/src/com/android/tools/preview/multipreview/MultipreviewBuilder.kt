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
  /**
   * @param path path of the file, containing that class, used for filtering
   * @param classByteCode class bytecode bytes
   */
  fun onClassBytecode(path: String, classByteCode: ByteArray)
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
 * methods allowed by [pathMethodsFilter] and [methodsFilter]. Allows specifying additional
 * precomputed [annotations] to speed up incremental processing.
 */
fun buildMultipreview(
  settings: MultipreviewSettings,
  annotations: Map<DerivedAnnotationRepresentation, AnnotationReferences> = emptyMap(),
  pathMethodsFilter: PathFilter = PathFilter.ALLOW_ALL,
  methodsFilter: MethodsFilter = MethodsFilter { true },
  provider: ClassBytecodeProvider,
): Multipreview {
  val multipreviewGraph = Graph()
  multipreviewGraph.addAnnotations(annotations)
  provider.forEachClass { path, bytes ->
    val cr = ClassReader(bytes)
    val className = cr.className.classPathToName
    val classVisitor = if ((cr.access and Opcodes.ACC_ANNOTATION) != 0) {
      val recorder = multipreviewGraph.addAnnotationNode(DerivedAnnotationRepresentation(className))
      AnnotationClassVisitor(settings.baseAnnotation, recorder)
    } else if (pathMethodsFilter.allowMethodsForPath(path)) {
      MethodsClassVisitor(settings, cr.className.classPathToName, multipreviewGraph, methodsFilter)
    } else {
      null
    }
    classVisitor?.let { v -> cr.accept(v, 0) }
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
                      classProcessor.onClassBytecode(path, stream.readAllBytes())
                  }
              }
          }
      } else if (File(path).isDirectory) {
          File(path).walk().filter { it.name.endsWith(".class") }.forEach {
              classProcessor.onClassBytecode(path, it.readBytes())
          }
      }
  }
}

fun buildMultipreview(
  settings: MultipreviewSettings,
  paths: Collection<String>,
  annotations: Map<DerivedAnnotationRepresentation, AnnotationReferences> = emptyMap(),
  pathFilter: PathFilter = PathFilter.ALLOW_ALL,
  methodsFilter: MethodsFilter = MethodsFilter { true }
): Multipreview {
  return buildMultipreview(settings, annotations, pathFilter, methodsFilter) { processor ->
    forEachClass(paths, processor::onClassBytecode)
  }
}

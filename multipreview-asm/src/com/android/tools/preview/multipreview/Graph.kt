package com.android.tools.preview.multipreview

/**
 * Implementation of the [Multipreview] based on a graph where leaf nodes are instances of base
 * preview annotations and intermediate nodes are either annotated methods or derived annotations.
 */
internal class Graph : Multipreview {

  private val methodNodes = mutableMapOf<MethodRepresentation, AnnotationReferences>()
  private val annotationNodes =
    mutableMapOf<DerivedAnnotationRepresentation, AnnotationReferences>()

  fun addMethodNode(methodId: MethodRepresentation, annotationsRecorder: AnnotationReferences) {
    methodNodes[methodId] = annotationsRecorder
  }

  fun addAnnotationNode(annotationId: DerivedAnnotationRepresentation): AnnotationRecorder {
    val references = AnnotationReferencesRecorder()
    annotationNodes[annotationId] = references
    return references
  }

  override val methods: Set<MethodRepresentation> = methodNodes.keys

  /**
   * Cleans the graph off the redundant annotations (that are neither base nor derived annotations)
   * and methods (that are annotated by neither base nor derived annotations). This should be run
   * when all the data about methods and annotations is added to the graph, and it is capable of
   * resolving all the dependencies and remove the unrelated entities.
   */
  internal fun prune() {
    val prunedAnnotations = mutableSetOf<DerivedAnnotationRepresentation>()
    val annotationsAbove = mutableSetOf<DerivedAnnotationRepresentation>()
    annotationNodes.keys.toList().forEach {
      prune(it, prunedAnnotations, annotationsAbove)
    }

    methodNodes.keys.toList().forEach { method ->
      methodNodes[method]?.let { node ->
        val nonLeafs = node.derivedAnnotations.filter { it in annotationNodes.keys }
        if (nonLeafs.isEmpty() && node.baseAnnotations.isEmpty()) {
          methodNodes.remove(method)
        } else {
          node.derivedAnnotations.clear()
          node.derivedAnnotations.addAll(nonLeafs)
        }
      }
    }
  }

  private fun prune(
    annotationId: DerivedAnnotationRepresentation,
    prunedAnnotations: MutableSet<DerivedAnnotationRepresentation>,
    annotationsAbove: MutableSet<DerivedAnnotationRepresentation>
  ) {
    if (annotationId in annotationsAbove) {
      // Just ignore the annotation if it is recursive.
      annotationNodes.remove(annotationId)
      return
    }
    annotationsAbove.add(annotationId)
    try {
      if (annotationId in prunedAnnotations) {
        return
      }
      val node = annotationNodes[annotationId]
      if (node != null) {
        // Derived annotations can't be leafs, they should either be annotated by base annotations
        // or by other derived annotations. Therefore, they should be keys in [annotationNodes]
        val nonLeafs = node.derivedAnnotations.filter { it in annotationNodes.keys }
        node.derivedAnnotations.clear()
        node.derivedAnnotations.addAll(nonLeafs)
        val toRemove = mutableSetOf<DerivedAnnotationRepresentation>()
        node.derivedAnnotations.forEach {
          prune(it, prunedAnnotations, annotationsAbove)
          if (annotationNodes[it] == null) {
            toRemove.add(it)
          }
        }
        node.derivedAnnotations.removeAll(toRemove)
        if (node.derivedAnnotations.isEmpty() && node.baseAnnotations.isEmpty()) {
          annotationNodes.remove(annotationId)
        }
      }
      prunedAnnotations.add(annotationId)
    } finally {
      annotationsAbove.remove(annotationId)
    }
  }

  /**
   * This has O(N^2) complexity in the worst case. If this is a performance bottleneck, it can be
   * improved with https://usaco.guide/plat/merging?lang=cpp.
   */
  private fun AnnotationReferences.resolve(
    cache: MutableMap<AnnotationReferences, Set<BaseAnnotationRepresentation>> = mutableMapOf()
  ): Set<BaseAnnotationRepresentation> {
    val resolution = this.baseAnnotations.toSet() + this.derivedAnnotations.flatMap {
      annotationNodes[it]?.let{ refs ->
        cache[refs] ?: refs.resolve(cache)
      } ?: emptySet()
    }
    cache[this] = resolution
    return resolution
  }

  override fun getAnnotations(method: MethodRepresentation): Set<BaseAnnotationRepresentation> =
    methodNodes[method]?.resolve() ?: emptySet()
}

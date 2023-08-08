package com.android.tools.preview.multipreview

/** A straightforward implementation of combined [AnnotationReferences] and [AnnotationRecorder]. */
class AnnotationReferencesRecorder : AnnotationReferences, AnnotationRecorder {

  override val baseAnnotations = mutableListOf<BaseAnnotationRepresentation>()

  override val derivedAnnotations = mutableSetOf<DerivedAnnotationRepresentation>()

  override fun recordBaseAnnotation(baseAnnotation: BaseAnnotationRepresentation) {
    baseAnnotations.add(baseAnnotation)
  }

  override fun recordDerivedAnnotation(derivedAnnotation: DerivedAnnotationRepresentation) {
    derivedAnnotations.add(derivedAnnotation)
  }
}

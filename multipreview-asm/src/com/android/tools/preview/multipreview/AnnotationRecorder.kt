package com.android.tools.preview.multipreview

/**
 * Records entries of base and derived annotations for a method or an annotation class.
 */
internal interface AnnotationRecorder {

  /**
   * Records an application of the base annotation, this will normally include recording the
   * parameters specified in the instance of the base annotation.
   */
  fun recordBaseAnnotation(baseAnnotation: BaseAnnotationRepresentation)

  /**
   * Records an application of a derived annotation. It will normally be just some annotation
   * identification (e.g. FQCN), since derived annotations do not have parameters.
   */
  fun recordDerivedAnnotation(derivedAnnotation: DerivedAnnotationRepresentation)
}

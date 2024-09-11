/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.lint.client.api

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.tools.lint.client.api.LintDriver.Companion.handleDetectorError
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.ResourceContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import com.android.tools.lint.detector.api.resolveManifestName
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Specialized visitor for running detectors on resources: typically XML documents, but also binary
 * resources.
 *
 * It operates in two phases:
 * 1. First, it computes a set of maps where it generates a map from each significant element name,
 *    and each significant attribute name, to a list of detectors to consult for that element or
 *    attribute name. The set of element names or attribute names (or both) that a detector is
 *    interested in is provided by the detectors themselves.
 * 2. Second, it iterates over the document a single time. For each element and attribute it looks
 *    up the list of interested detectors, and runs them.
 *
 * It also notifies all the detectors before and after the document is processed such that they can
 * do pre- and post-processing.
 */
internal class ResourceVisitor(
  driver: LintDriver,
  private val allDetectors: List<XmlScanner>,
  private val binaryDetectors: List<Detector>?,
) {
  private val elementToCheck: Map<String, List<XmlScanner>>
  private val attributeToCheck: Map<String?, List<XmlScanner>>
  private val allElementDetectors: List<XmlScanner>
  private val allAttributeDetectors: List<XmlScanner>
  private val annotationHandler: AnnotationHandler?

  init {
    val elementToCheck = HashMap<String, MutableList<XmlScanner>>()
    val attributeToCheck = HashMap<String?, MutableList<XmlScanner>>()
    val allElementDetectors = ArrayList<XmlScanner>()
    val allAttributeDetectors = ArrayList<XmlScanner>()

    var annotationsMap: MutableMap<String, MutableList<SourceCodeScanner>>? = null
    for (detector in allDetectors) {
      val attributes = detector.getApplicableAttributes()
      if (attributes === XmlScannerConstants.ALL) {
        allAttributeDetectors.add(detector)
      } else {
        attributes?.forEach { attribute ->
          attributeToCheck.getOrPut(attribute) { ArrayList() }.add(detector)
        }
      }
      val elements = detector.getApplicableElements()
      if (elements === XmlScannerConstants.ALL) {
        allElementDetectors.add(detector)
      } else {
        elements?.forEach { element ->
          elementToCheck.getOrPut(element) { ArrayList() }.add(detector)
        }
      }

      if (detector is SourceCodeScanner) {
        val annotations = detector.applicableAnnotations()
        if (annotations != null) {
          if (annotationsMap == null) {
            annotationsMap = HashMap()
          }
          for (annotation in annotations) {
            annotationsMap.getOrPut(annotation) { ArrayList() }.add(detector)
          }
        }
      }
    }

    this.elementToCheck = elementToCheck
    this.attributeToCheck = attributeToCheck
    this.allElementDetectors = allElementDetectors
    this.allAttributeDetectors = allAttributeDetectors
    this.annotationHandler = annotationsMap?.let { AnnotationHandler(driver, it) }
  }

  fun visitFile(context: XmlContext) {
    try {
      for (check in allDetectors) {
        check.beforeCheckFile(context)
        check.visitDocument(context, context.document)
      }

      if (
        elementToCheck.isNotEmpty() ||
          attributeToCheck.isNotEmpty() ||
          allAttributeDetectors.isNotEmpty() ||
          allElementDetectors.isNotEmpty()
      ) {
        visitElement(context, context.document.documentElement)
      }

      for (check in allDetectors) {
        check.afterCheckFile(context)
      }
    } catch (e: Throwable) {
      handleDetectorError(context, context.driver, e)
    }
  }

  private fun String.isLikelyClassName(): Boolean {
    val inPackage = indexOf('.') != -1
    if (inPackage && length >= 2) {
      var prev = '.'
      for (c in this) {
        if (c == '.' && prev != '.') {
          // ok
        } else if (prev == '.' && c.isJavaIdentifierStart()) {
          // ok
        } else if (c.isJavaIdentifierPart()) {
          // ok
        } else {
          return false
        }
        prev = c
      }
      // e.g. "Nov."
      return prev != '.'
    } else if (!inPackage && isNotEmpty()) {
      var index = 0
      for (c in this) {
        if (index == 0 && c.isJavaIdentifierStart()) {
          // ok
        } else if (c.isJavaIdentifierPart()) {
          // ok
        } else {
          return false
        }
        index++
      }
      return true
    }
    return false
  }

  private fun visitElement(context: XmlContext, element: Element) {
    val annotationHandler = annotationHandler
    val elementChecks = elementToCheck[element.localName]
    elementChecks?.forEach { check -> check.visitElement(context, element) }
    allElementDetectors.forEach { check -> check.visitElement(context, element) }

    if (attributeToCheck.isNotEmpty() || allAttributeDetectors.isNotEmpty()) {
      val attributes = element.attributes
      for (i in 0 until attributes.length) {
        val attribute = attributes.item(i) as Attr
        val name = attribute.localName ?: attribute.name
        attributeToCheck[name]?.forEach { check -> check.visitAttribute(context, attribute) }
        allAttributeDetectors.forEach { check -> check.visitAttribute(context, attribute) }

        // Class attribute?
        if (annotationHandler != null) {
          var className = attribute.value
          if (
            className.startsWith(".") &&
              context.file.path.endsWith(ANDROID_MANIFEST_XML) &&
              attribute.localName == ATTR_NAME &&
              attribute.namespaceURI == ANDROID_URI
          ) {
            // Manifest? Resolve package names:
            className = resolveManifestName(element, context.project)
          }

          if (className.isLikelyClassName()) {
            visitClassReference(className, context, attribute)
          }
        }
      }
    }

    // Visit children
    val childNodes = element.childNodes
    for (i in 0 until childNodes.length) {
      val child = childNodes.item(i)
      if (child.nodeType == Node.ELEMENT_NODE) {
        visitElement(context, child as Element)
      }
    }

    // Annotations?
    val tagName = element.tagName
    if (annotationHandler != null && tagName.isLikelyClassName()) {
      visitClassReference(tagName, context, element)
    }

    // Post hooks
    elementChecks?.forEach { check -> check.visitElementAfter(context, element) }
    allElementDetectors.forEach { check -> check.visitElementAfter(context, element) }
  }

  private fun visitClassReference(fqn: String, context: XmlContext, reference: Node) {
    val parser = context.client.getUastParser(context.project)
    val psiClass = parser.evaluator.findClass(fqn.replace('$', '.'))
    if (psiClass != null) {
      annotationHandler?.visitXmlClassReference(context, reference, psiClass)
    }
  }

  fun visitBinaryResource(context: ResourceContext) {
    for (check in binaryDetectors ?: return) {
      check.beforeCheckFile(context)
      check.checkBinaryResource(context)
      check.afterCheckFile(context)
    }
  }
}

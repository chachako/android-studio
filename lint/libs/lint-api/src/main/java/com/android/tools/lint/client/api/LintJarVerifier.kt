/*
 * Copyright (C) 2021 The Android Open Source Project
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

@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.android.tools.lint.client.api

import com.android.SdkConstants.CONSTRUCTOR_NAME
import com.android.SdkConstants.DOT_CLASS
import com.android.tools.lint.helpers.readAllBytes
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.Byte
import java.lang.Double
import java.lang.Float
import java.lang.Long
import java.lang.Short
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.util.jar.JarInputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassReader.SKIP_DEBUG
import org.objectweb.asm.ClassReader.SKIP_FRAMES
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.Type

/**
 * Given a lint jar file, checks to see if the jar file looks compatible with the current version of
 * lint.
 */
class LintJarVerifier(
  private val client: LintClient,
  private val jarFile: File,
  bytes: ByteArray,
  private val skip: Boolean = false,
) : ClassVisitor(ASM9) {
  constructor(
    client: LintClient,
    jarFile: File,
    skip: Boolean = false,
  ) : this(client, jarFile, jarFile.readBytes(), skip)
  /**
   * Is the class with the given [internal] class name part of an API we want to check for validity?
   */
  private fun isRelevantApi(internal: String): Boolean {
    val relevant =
      internal.startsWith("com/android/") ||
        // Imported APIs
        internal.startsWith("org/jetbrains/uast") ||
        internal.startsWith("org/jetbrains/kotlin") ||
        internal.startsWith("com/intellij")
    // Libraries unlikely to change: org.w3c.dom, org.objectweb.asm, org.xmlpull, etc.

    return relevant && !bundledClasses.contains(internal)
  }

  /**
   * Returns true if the lintJar does not contain any classes referencing lint APIs that are not
   * valid in the current class loader.
   */
  fun isCompatible(): Boolean {
    return incompatibleReference == null
  }

  /**
   * Returns whether the invalid reference was not accessible rather than not available. This method
   * is only relevant if [isCompatible] returned false.
   */
  fun isInaccessible(): Boolean {
    return inaccessible
  }

  fun getVerificationThrowable(): Throwable? = verifyProblem

  private fun verify(lintJarBytes: ByteArray) {
    if (skip) {
      return
    }
    // Scans through the bytecode for all the classes in lint.jar, and
    // checks any class, method or field reference accessing the Lint API
    // and makes sure that API is found in the bytecode
    val classes = mutableMapOf<String, ByteArray>()
    JarInputStream(ByteArrayInputStream(lintJarBytes)).use { jar ->
      var entry = jar.nextEntry
      while (entry != null) {
        val directory = entry.isDirectory
        val name = entry.name
        if (!directory && name.endsWith(DOT_CLASS)) {
          classes[name] = jar.readAllBytes(entry)
        }

        entry = jar.nextEntry
      }
    }

    verify(classes)
  }

  private fun verify(classes: Map<String, ByteArray>) {
    classes.forEach { (name, _) ->
      // Note that jar file internal names always use forward slash, not File.separator,
      // so we can compute the internal name by just dropping the .class suffix
      bundledClasses.add(name.removeSuffix(DOT_CLASS))
    }

    for ((name, bytes) in classes) {
      currentClassFile = name
      val reader = ClassReader(bytes)
      reader.accept(this, SKIP_DEBUG or SKIP_FRAMES)
      if (incompatibleReference != null) {
        break
      }
    }

    (classLoader as? URLClassLoader)?.close()
  }

  /** Returns a message describing the incompatibility */
  fun describeFirstIncompatibleReference(): String {
    val reference = incompatibleReference ?: return "Compatible"
    val index = reference.indexOf('#')
    if (index == -1) {
      return Type.getObjectType(reference).className.replace('$', '.')
    }
    val className = Type.getObjectType(reference.substring(0, index)).className.replace('$', '.')
    val paren = reference.indexOf('(')
    if (paren == -1) {
      // Field
      return className + "#" + reference.substring(index + 1)
    }

    // Method
    val sb = StringBuilder(className).append(": ")
    val descriptor = reference.substring(paren)
    val name = reference.subSequence(index + 1, paren)
    val arguments = Type.getArgumentTypes(descriptor)
    if (name == CONSTRUCTOR_NAME) {
      sb.append(className.substring(className.lastIndexOf('.') + 1))
    } else {
      val returnType = Type.getReturnType(descriptor).className
      sb.append(returnType).append(' ')
      sb.append(name)
    }
    sb.append('(')
    sb.append(arguments.joinToString(",") { it.className })
    sb.append(')')
    return sb.toString()
  }

  /** Returns the class file containing the invalid reference. */
  fun getReferenceClassFile(): String {
    return incompatibleReferencer!! // should only be called if incompatibleReference is non null
  }

  /** An exception thrown if there is some other verification problem (invalid class file etc) */
  private var verifyProblem: Throwable? = null

  /** The internal name of the invalid reference. */
  private var incompatibleReference: String? = null

  /** The internal names of the classes in the Jar file */
  private val bundledClasses: MutableSet<String> = mutableSetOf()

  /** The class file containing the reference to [incompatibleReference] */
  private var incompatibleReferencer: String? = null

  /** Whether the incompatible reference was not accessible rather than not available */
  private var inaccessible = false

  private val methodVisitor =
    object : MethodVisitor(ASM9) {
      override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean,
      ) {
        checkMethod(owner, name, descriptor)
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
      }

      override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String?) {
        checkField(owner, name)
        super.visitFieldInsn(opcode, owner, name, descriptor)
      }
    }

  /**
   * Checks that the class for the given [internal] name is valid: relevant and exists in the
   * current class node. If not, this method sets the [incompatibleReference] property.
   */
  private fun checkClass(internal: String) {
    if (isRelevantApi(internal)) {
      try {
        // Ignoring return value: what we're looking for here is a throw
        getClass(internal)
      } catch (e: Throwable) {
        incompatibleReference = internal
        incompatibleReferencer = currentClassFile
      }
    }
  }

  /** Count number of visited elements: for debugging statistics only. */
  var apiCount = 0
    private set

  /** Current class file being visited */
  private var currentClassFile: String? = null
  /** The current class being visited */
  private var currentClass: String? = null
  /** The super class of the current class */
  private var currentSuperClass: String? = null
  /**
   * A class loader for the current jar file (initialized lazily if needed; we only do this if we
   * have to check whether a call to a protected API method is valid.
   */
  private var classLoader: ClassLoader? = null

  /**
   * Checks that the method for the given containing class [owner] and method [name] is valid:
   * relevant and exists in the current class node. If not, this method sets the
   * [incompatibleReference] property.
   */
  private fun checkMethod(owner: String, name: String, descriptor: String) {
    if (isRelevantApi(owner)) {
      try {
        apiCount++
        val method = getMethod(owner, name, descriptor) // expected side effect: throws if invalid
        checkModifiers(owner, method.modifiers)
      } catch (e: Throwable) {
        incompatibleReference = "$owner#$name$descriptor"
        incompatibleReferencer = currentClassFile
      }
    }
  }

  /**
   * Checks that the field for the given containing class [owner] and field [name] is valid:
   * relevant and exists in the current class node. If not, this method sets the
   * [incompatibleReference] property.
   */
  private fun checkField(owner: String, name: String) {
    if (isRelevantApi(owner)) {
      try {
        apiCount++
        val field = getField(owner, name) // expected side effect: throws if invalid
        checkModifiers(owner, field.modifiers)
      } catch (e: Throwable) {
        incompatibleReference = "$owner#$name"
        incompatibleReferencer = currentClassFile
      }
    }
  }

  /**
   * Given a call into a method or field in class [owner], where the field or method has access
   * level [modifiers], with a reference coming from [currentClass], checks whether the access is
   * valid (e.g. the API is public, or protected if we're coming from a subclass). If not, it throws
   * an exception.
   */
  private fun checkModifiers(owner: String, modifiers: Int) {
    if (
      (modifiers and Opcodes.ACC_PUBLIC) != 0 ||
        (modifiers and Opcodes.ACC_PROTECTED) != 0 && isCalledFromSubClass(owner)
    ) {
      return
    }
    inaccessible = true
    throw IllegalAccessException(owner)
  }

  private fun isCalledFromSubClass(owner: String): Boolean {
    // From direct subclass? Then we know protected is okay.
    if (currentSuperClass == owner) {
      return true
    }
    // Not from a direct subclass; let's check whether it's indirect. This is slower; we have to use
    // reflection etc. Thankfully this is rare.
    return try {
      val loader =
        classLoader
          ?: client.createUrlClassLoader(listOf(jarFile), this.javaClass.classLoader).also {
            classLoader = it
          }
      val currentClass = currentClass ?: return false
      val cls = Class.forName(currentClass.replace('/', '.'), false, loader)
      return isSubClass(cls, owner.replace('/', '.'), loader)
    } catch (ignore: Throwable) {
      // Class loading problem: we're unsure, assume it's okay
      true
    }
  }

  private fun isSubClass(currentClass: Class<*>?, target: String, loader: ClassLoader): Boolean {
    currentClass ?: return false
    if (currentClass.name == target) {
      return true
    }
    val superClass = currentClass.superclass
    if (isSubClass(superClass, target, loader)) {
      return true
    }
    for (itf in currentClass.interfaces) {
      if (isSubClass(itf, target, loader)) {
        return true
      }
    }
    return false
  }

  /** Loads the class of the given [internal] name */
  private fun getClass(internal: String): Class<*> {
    apiCount++
    val className = Type.getObjectType(internal).className
    return Class.forName(className, false, javaClass.classLoader)
  }

  /**
   * Returns the [Method] or [Constructor] referenced by the given containing class internal name,
   * [owner], the method name [name], and internal method descriptor. Will throw an exception if the
   * method or constructor does not exist.
   */
  private fun getMethod(owner: String, name: String, descriptor: String): Executable {
    // Initially I thought I should cache this but it turns out
    // this is already pretty fast -- all 137 lint.jar files on
    // maven.google.com as of today with combined size 2.8M is
    // analyzed in around one second -- e.g. 7.5ms per jar.
    val clz = getClass(owner)

    val argumentTypes =
      Type.getArgumentTypes(descriptor).map { type -> type.toTypeClass() }.toTypedArray()

    return if (name == CONSTRUCTOR_NAME) {
      try {
        clz.getDeclaredConstructor(*argumentTypes)
      } catch (e: Throwable) {
        clz.getConstructor(*argumentTypes)
      }
    } else {
      try {
        clz.getDeclaredMethod(name, *argumentTypes)
      } catch (e: Throwable) {
        clz.getMethod(name, *argumentTypes)
      }
    }
  }

  /**
   * Returns the [Field] referenced by the given containing class internal name, [owner], and the
   * field name. Will throw an exception if the field does not exist.
   */
  private fun getField(owner: String, name: String): Field {
    val clz = getClass(owner)
    return try {
      clz.getDeclaredField(name)
    } catch (e: Throwable) {
      clz.getField(name)
    }
  }

  override fun visit(
    version: Int,
    access: Int,
    name: String,
    signature: String?,
    superName: String?,
    interfaces: Array<out String>?,
  ) {
    currentClass = name
    currentSuperClass = superName
    superName?.let { checkClass(it) }
    interfaces?.let { it.forEach { internal -> checkClass(internal) } }
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitMethod(
    access: Int,
    name: String?,
    descriptor: String?,
    signature: String?,
    exceptions: Array<out String>?,
  ): MethodVisitor {
    return methodVisitor
  }

  init {
    try {
      verify(bytes)
    } catch (throwable: Throwable) {
      verifyProblem = throwable
    }
  }
}

/**
 * Given an ASM type compute the corresponding java.lang.Class. I really thought ASM would have this
 * functionality, and perhaps it does, but I could not find it.
 */
private fun Type.toTypeClass(): Class<out Any> {
  return when (descriptor) {
    "Z" -> java.lang.Boolean.TYPE
    "B" -> Byte.TYPE
    "C" -> Character.TYPE
    "S" -> Short.TYPE
    "I" -> Integer.TYPE
    "J" -> Long.TYPE
    "F" -> Float.TYPE
    "D" -> Double.TYPE
    "V" -> Void.TYPE
    else -> {
      when {
        descriptor.startsWith("L") -> Class.forName(className, false, javaClass.classLoader)
        descriptor.startsWith("[") ->
          java.lang.reflect.Array.newInstance(elementType.toTypeClass(), 0)::class.java
        else -> error("Unexpected internal type $descriptor")
      }
    }
  }
}

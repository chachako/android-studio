/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.tools.lint.UastEnvironment
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.JavaContext
import com.android.utils.FileUtils
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.PsiUtil
import java.io.File
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isNullLiteral
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ACC_ABSTRACT
import org.objectweb.asm.Opcodes.ACC_ANNOTATION
import org.objectweb.asm.Opcodes.ACC_ENUM
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_INTERFACE
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PROTECTED
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACC_SUPER
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.objectweb.asm.Opcodes.ACC_VOLATILE
import org.objectweb.asm.Opcodes.ATHROW
import org.objectweb.asm.Opcodes.DUP
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.IRETURN
import org.objectweb.asm.Opcodes.NEW
import org.objectweb.asm.Opcodes.V1_8
import org.objectweb.asm.Type

/**
 * Special test file which given a series of stub files can generate a .jar file with compiled class
 * files corresponding to the stubs.
 *
 * Note that this only works for stub files, not files with non-trivial method bodies. The main
 * complications this class deal with are generics signatures, annotations in APIs and field
 * initializers.
 *
 * It only works at the PSI class file level; it does not generate Kotlin augmented metadata files.
 * Therefore, you can include top level functions, companion objects etc, and the stubs will look as
 * they do from Java code.
 *
 * TODO: Use the kotlin metadata library to also construct binary content for things like package
 *   level methods; see
 *   https://github.com/JetBrains/kotlin/blob/master/libraries/kotlinx-metadata/jvm/ReadMe.md
 */
internal open class StubClassFile(
  into: String,
  override val type: BytecodeTestFile.Type,
  /** The test source files to be stubbed */
  val stubSources: List<TestFile>,
  /** Any library-only (needed for compilation, but not to be packaged) dependencies */
  val compileOnly: List<TestFile>
) : TestFile(), BytecodeTestFile {
  var task: TestLintTask? = null

  init {
    targetRelativePath = if (isArtifact(into)) artifactToJar(into) else into
  }

  override fun createFile(targetDir: File): File {
    if (type == BytecodeTestFile.Type.SOURCE_AND_BYTECODE) {
      for (file in stubSources) {
        val path = file.targetRelativePath
        if (path.endsWith(DOT_JAVA) || path.endsWith(DOT_KT)) {
          file.createFile(targetDir)
        }
      }
    }

    val classFiles = getBytecodeFiles().toMutableList()

    // Always include resource files
    for (file in stubSources) {
      val path = file.targetRelativePath
      if (!path.endsWith(DOT_JAVA) && !path.endsWith(DOT_KT)) {
        classFiles.add(file)
      }
    }

    val target = targetRelativePath
    return if (target.endsWith(SdkConstants.DOT_JAR)) {
      // Combine multiple class files into a single jar
      val array = classFiles.toTypedArray()
      JarTestFile(target).files(*array).createFile(targetDir)
    } else {
      for (file in classFiles) {
        file.createFile(targetDir)
      }
      // This isn't right; there's more than one java.io.File created, but the
      // caller of this method ignores the return value.
      File(targetRelativePath)
    }
  }

  override fun getSources(): List<TestFile> {
    return stubSources +
      (compileOnly.filter {
        it.targetRelativePath.endsWith(DOT_JAVA) || it.targetRelativePath.endsWith(DOT_KT)
      })
  }

  private var bytecodeFiles: List<TestFile>? = null

  override fun getBytecodeFiles(): List<TestFile> {
    return bytecodeFiles ?: createClassFiles().also { bytecodeFiles = it }
  }

  override fun getGeneratedPaths(): List<String> {
    return if (targetRelativePath.endsWith(SdkConstants.DOT_JAR)) {
      listOf(targetRelativePath)
    } else {
      getBytecodeFiles().map { it.targetRelativePath }.toList()
    }
  }

  private fun createClassFiles(): List<TestFile> {
    val tempDirectory = createTempDirectory() // need a temp directory to store the byte code
    val folder = TemporaryFolder(tempDirectory)
    try {
      folder.create()
      val (contexts, disposable) =
        parse(
          temporaryFolder = folder,
          sdkHome = task?.sdkHome,
          testFiles = (stubSources + compileOnly).toTypedArray()
        )
      try {
        val filtered =
          contexts.filter { context ->
            stubSources.any { testFile ->
              context.file.path.replace('\\', '/').endsWith(testFile.targetRelativePath)
            }
          }

        val classFiles = mutableListOf<TestFile>()
        for (context in filtered) {
          // Make sure we don't have unresolved symbols in the stubs
          val task = task
          if (task != null && !task.allowCompilationErrors) {
            context.checkFile(context.uastFile, task, isStub = true)
          }

          context.uastFile?.let { getByteCode(context, it) }?.let { classFiles.addAll(it) }
        }
        return classFiles
      } finally {
        Disposer.dispose(disposable)
        if (SystemInfo.isWindows) {
          // Sometimes the runtime environment is still hanging on to .jar files and Windows file
          // locking blocks
          // the below file deletes.
          UastEnvironment.disposeApplicationEnvironment()
        }
      }
    } finally {
      folder.delete()
      FileUtils.deleteRecursivelyIfExists(tempDirectory)
    }
  }

  private fun getByteCode(context: JavaContext, file: UFile): List<TestFile> {
    val evaluator = context.evaluator

    val sourcePsi = file.sourcePsi
    if (file.classes.isEmpty() && sourcePsi is PsiJavaFile) {
      return generatePackageInfo(sourcePsi)
    }

    val classes = mutableListOf<TestFile>()
    file.accept(
      object : AbstractUastVisitor() {
        private fun getModifiers(owner: PsiModifierListOwner): Int {
          return getModifiers(owner.modifierList)
        }

        private fun getModifiers(modifierList: PsiModifierList?): Int {
          var modifiers: Int =
            when {
              modifierList == null -> return 0
              modifierList.hasModifierProperty(PsiModifier.PUBLIC) -> ACC_PUBLIC
              modifierList.hasModifierProperty(PsiModifier.PROTECTED) -> ACC_PROTECTED
              modifierList.hasModifierProperty(PsiModifier.PRIVATE) -> ACC_PRIVATE
              else -> 0
            }
          if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            modifiers = modifiers or ACC_STATIC
          }
          if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
            modifiers = modifiers or ACC_ABSTRACT
          }
          if (modifierList.hasModifierProperty(PsiModifier.FINAL)) {
            modifiers = modifiers or ACC_FINAL
          }
          if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) {
            modifiers = modifiers or ACC_VOLATILE
          }
          return modifiers
        }

        override fun visitClass(node: UClass): Boolean {
          // Developed using something like
          //    "java jdk.internal.org.objectweb.asm.util.ASMifier
          // com/example/myapplication/DiffUtil\$ItemCallback.class"
          val cls = node.javaPsi
          val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
          val internalName = cls.internalName()
          if (internalName != null) {
            val superClass = cls.superTypes.firstOrNull()?.internalName() ?: "java/lang/Object"
            val classModifierList = cls.modifierList
            var modifiers = getModifiers(classModifierList) or ACC_SUPER
            if (cls.isInterface) {
              modifiers = modifiers or ACC_INTERFACE
            }
            if (cls.isAnnotationType) {
              modifiers = modifiers or ACC_ANNOTATION
            } else if (cls.isEnum) {
              modifiers = modifiers or ACC_ENUM
            }
            val classSignature = getGenericsSignature(cls)
            val interfaces = typeArray(cls.interfaces)
            cw.visit(V1_8, modifiers, internalName, classSignature, superClass, interfaces)

            visitAnnotations(classModifierList, cw::visitAnnotation)

            val outerClass = node.getParentOfType<UClass>()
            if (outerClass == null) {
              cw.visitSource(context.file.name, "")
            } else {
              cw.visitOuterClass(outerClass.internalName(), null, null)
            }

            for (inner in cls.innerClasses) {
              val innerName = inner.internalName() ?: continue
              val innerModifiers = getModifiers(inner)
              cw.visitInnerClass(innerName, internalName, inner.name, innerModifiers)
            }

            // default constructor
            if (
              PsiUtil.hasDefaultConstructor(cls) &&
                !cls.isEnum &&
                !cls.isInterface &&
                !cls.isAnnotationType
            ) {
              val mv: MethodVisitor = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null)
              mv.visitCode()
              mv.visitMaxs(0, 0)
              mv.visitEnd()
            }

            val containingClass = cls.containingClass

            val hasSyntheticConstructorArgument0: Boolean =
              if (containingClass != null) {
                val outerClassFieldVisitor =
                  cw.visitField(
                    ACC_FINAL + ACC_SYNTHETIC,
                    "this$1",
                    "L${containingClass.internalName()};",
                    null,
                    null
                  )
                outerClassFieldVisitor.visitEnd()
                classModifierList?.hasModifierProperty(PsiModifier.STATIC) != true
              } else {
                false
              }

            for (method in node.uastDeclarations.filterIsInstance<UMethod>()) {
              val psiMethod = method.javaPsi
              val exceptions = typeArray(psiMethod.throwsList.referencedTypes)
              @Suppress("DEPRECATION") // deliberate use of internal names
              val description = evaluator.getInternalDescription(psiMethod) ?: continue
              val methodSignature =
                getGenericsSignature(
                  psiMethod,
                  if (hasSyntheticConstructorArgument0) containingClass else null
                )
              val isConstructor = psiMethod.isConstructor
              val methodName = if (isConstructor) "<init>" else psiMethod.name

              if (!method.isStub()) {
                error(
                  "Method `${cls.qualifiedName}.${psiMethod.name}$description` is not just a stub method;\n" +
                    "it contains code, which the testing infrastructure bytecode stubber can't handle.\n" +
                    "You'll need to switch to a `compiled` or `bytecode` test file type instead (where\n" +
                    "you precompile the source code using a compiler), *or*, if the method body etc isn't\n" +
                    "necessary for the test, remove it and replace with a simple return or throw.\n" +
                    "Method body: ${method.uastBody?.sourcePsi?.text ?: method.sourcePsi?.text}\n"
                )
              }

              val mv =
                cw.visitMethod(
                  getModifiers(psiMethod),
                  methodName,
                  description,
                  methodSignature,
                  exceptions
                )
              visitAnnotations(psiMethod.modifierList, mv::visitAnnotation)

              if (isConstructor && hasSyntheticConstructorArgument0) {
                val av = mv.visitParameterAnnotation(0, "Ljava/lang/Synthetic;", false)
                av.visitEnd()
                mv.visitParameter("this$1", 0)
              }

              val parameters = psiMethod.parameterList.parameters
              for (index in parameters.indices) {
                val parameter = parameters[index]
                val parameterIndex = if (hasSyntheticConstructorArgument0) index + 1 else index
                visitAnnotations(
                  parameter.modifierList,
                  null,
                  parameterIndex,
                  mv::visitParameterAnnotation
                )
                mv.visitParameter(parameter.name, getModifiers(parameter))
              }

              mv.visitCode()
              if (description.endsWith(")I")) { // return 0 for int methods
                mv.visitInsn(ICONST_0)
                mv.visitInsn(IRETURN)
                mv.visitMaxs(1, 1)
              } else {
                mv.visitTypeInsn(
                  NEW,
                  "java/lang/UnsupportedOperationException"
                ) // otherwise throw unsupported exception
                mv.visitInsn(DUP)
                mv.visitMethodInsn(
                  INVOKESPECIAL,
                  "java/lang/UnsupportedOperationException",
                  "<init>",
                  "()V",
                  false
                )
                mv.visitInsn(ATHROW)
                mv.visitMaxs(2, 3)
              }
              mv.visitEnd()
            }

            for (field in cls.fields) {
              @Suppress("DEPRECATION") // deliberate use of internal names
              val descriptor = evaluator.getInternalDescription(field)
              val fieldSignature = getGenericsSignature(field)
              val fv =
                cw.visitField(
                  getModifiers(field),
                  field.name,
                  descriptor,
                  fieldSignature,
                  if (field is PsiEnumConstant) null else field.computeConstantValue()
                )
              visitAnnotations(field.modifierList, fv::visitAnnotation)
              fv.visitEnd()
            }

            cw.visitEnd()
            val classFile = internalName + SdkConstants.DOT_CLASS
            classes.add(TestFiles.bytes(classFile, cw.toByteArray()))
          }

          return super.visitClass(node)
        }
      }
    )

    return classes
  }

  private fun generatePackageInfo(sourcePsi: PsiJavaFile): List<TestFile> {
    // package-info isn't really a class in the PSI sense so create a .class file manually
    val packageStatement = sourcePsi.packageStatement ?: return emptyList()
    //noinspection ExternalAnnotations
    val annotations = packageStatement.annotationList.annotations
    if (annotations.isEmpty()) {
      return emptyList()
    }
    val cw = ClassWriter(0)
    val internalName = packageStatement.packageName.replace('.', '/') + "/package-info"
    cw.visit(
      V1_8,
      ACC_ABSTRACT + ACC_INTERFACE + ACC_SYNTHETIC,
      internalName,
      null,
      "java/lang/Object",
      null
    )
    visitAnnotations(annotations, cw::visitAnnotation)
    cw.visitEnd()
    val classFile = internalName + SdkConstants.DOT_CLASS
    return listOf(TestFiles.bytes(classFile, cw.toByteArray()))
  }

  private fun PsiClass.internalName(): String? {
    return qualifiedName?.let { ClassContext.getInternalName(it) }
  }

  private fun PsiAnnotation.internalName(): String? {
    return qualifiedName?.let { ClassContext.getInternalName(it) }
  }

  private fun PsiClassType.internalName(): String {
    return canonicalText.let { ClassContext.getInternalName(it) }
  }

  private fun typeArray(types: Array<PsiClassType>): Array<String>? {
    if (types.isEmpty()) {
      return null
    }
    return types.map { it.internalName() }.toTypedArray().let { if (it.isEmpty()) null else it }
  }

  private fun typeArray(types: Array<PsiClass>): Array<String>? {
    if (types.isEmpty()) {
      return null
    }
    return types
      .mapNotNull { it.internalName() }
      .toTypedArray()
      .let { if (it.isEmpty()) null else it }
  }

  @Suppress("ExternalAnnotations")
  private fun visitAnnotations(
    modifierList: PsiModifierList?,
    visit: ((String, Boolean) -> AnnotationVisitor)? = null,
    index: Int = -1,
    parameterVisit: ((Int, String, Boolean) -> AnnotationVisitor)? = null
  ) {
    modifierList ?: return
    visitAnnotations(modifierList.annotations, visit, index, parameterVisit)
  }

  private fun visitAnnotations(
    annotations: Array<PsiAnnotation>,
    visit: ((String, Boolean) -> AnnotationVisitor)? = null,
    index: Int = -1,
    parameterVisit: ((Int, String, Boolean) -> AnnotationVisitor)? = null
  ) {
    for (annotation in annotations) {
      visitAnnotation(annotation, visit, index, parameterVisit)
    }
  }

  private fun visitAnnotation(
    annotation: PsiAnnotation,
    visit: ((String, Boolean) -> AnnotationVisitor)?,
    index: Int,
    parameterVisit: ((Int, String, Boolean) -> AnnotationVisitor)?
  ) {
    val internalName = annotation.internalName() ?: return
    val annotationType = annotation.toUElement()?.tryResolve() as? PsiClass
    if (annotationType.hasSourceRetention()) {
      return
    }
    val visitor =
      if (visit != null) visit("L$internalName;", false)
      else if (parameterVisit != null) {
        parameterVisit(index, "L$internalName;", false)
      } else {
        error("Missing visitor")
      }
    for (attribute in annotation.parameterList.attributes) {
      writeAttribute(attribute, visitor, annotationType)
    }
    visitor.visitEnd()
  }

  private fun PsiClass?.hasSourceRetention(): Boolean {
    this ?: return false
    val retention = getAnnotation("java.lang.annotation.Retention")
    if (retention != null) {
      (retention.attributes.firstOrNull()?.attributeValue as? JvmAnnotationEnumFieldValue)
        ?.fieldName
        ?.let { name ->
          if (name == "SOURCE") {
            return true
          }
        }
    }

    return false
  }

  private fun writeAttribute(
    attribute: PsiNameValuePair,
    visitor: AnnotationVisitor,
    annotationType: PsiClass?
  ) {
    val name = attribute.name ?: ATTR_VALUE
    val element = attribute.value

    val attributeType = annotationType?.findMethodsByName(name, false)?.firstOrNull()?.returnType
    if (attributeType is PsiArrayType) {
      val arrayVisitor = visitor.visitArray(name)
      writeAttribute(null, element, arrayVisitor, attributeType.componentType)
      arrayVisitor.visitEnd()
    } else {
      writeAttribute(name, element, visitor, attributeType)
    }
  }

  private fun writeAttribute(
    name: String?,
    value: PsiElement?,
    visitor: AnnotationVisitor,
    type: PsiType?
  ) {
    if (value is PsiReference) {
      val resolved = value.resolve() ?: return
      writeAttribute(name, resolved, visitor, type)
    } else if (value is PsiField) {
      if (value is PsiEnumConstant) {
        val enumTypeName = value.containingClass?.internalName()
        visitor.visitEnum(name, "L$enumTypeName;", value.name)
      } else {
        val constant = value.computeConstantValue()
        if (constant != null) {
          visitor.visit(name, constant)
          return
        }
        error("Annotation attribute type not yet supported: $value")
      }
    } else if (value is PsiLiteral) {
      val literalValue = value.value
      if (literalValue is Pair<*, *>) {
        // Hack: Kotlin enum references are represented by a KtLightPsiLiteral evaluating to
        // Pair<ClassId,Name>.
        val (enumType, enumValue) = literalValue
        check(enumType is ClassId && enumValue is Name) {
          "Expected Kotlin enum value; instead found $literalValue"
        }
        val enumTypeName = ClassContext.getInternalName(enumType.asFqNameString())
        visitor.visitEnum(name, "L$enumTypeName;", enumValue.identifier)
      } else {
        visitor.visit(name, literalValue)
      }
    } else if (value is PsiArrayInitializerMemberValue) {
      val initializers = value.initializers
      if (name == null) {
        // Already created array in caller (where PSI has a single element but bytecode should have
        // array to match property)
        for (initializer in initializers) {
          writeAttribute(null, initializer, visitor, type)
        }
      } else {
        val arrayVisitor = visitor.visitArray(name)
        val componentType = (type as? PsiArrayType)?.componentType ?: type
        for (initializer in initializers) {
          writeAttribute(null, initializer, arrayVisitor, componentType)
        }
        arrayVisitor.visitEnd()
      }
    } else if (value is PsiClassObjectAccessExpression) {
      val classType = (value.operand.type as? PsiClassType)?.internalName()
      if (classType != null) {
        visitor.visit(name, Type.getType("L$classType;"))
      }
    } else if (value is PsiAnnotation) {
      val visit: ((String, Boolean) -> AnnotationVisitor) = { descriptor, _ ->
        visitor.visitAnnotation(name, descriptor)
      }
      visitAnnotation(value, visit, -1, null)
    } else {
      // Handle some basic expressions in the stub sources too, such as `static final int constant =
      // "test".length();`
      val constant =
        ConstantEvaluator.evaluate(null, value as PsiElement)
          ?: error("Annotation attribute type not yet supported: $value")
      if (constant is PsiElement) {
        error("Annotation attribute type not yet supported: $constant (from $name=$constant")
      }
      visitor.visit(name, constant)
    }
  }

  companion object {
    /**
     * Is this method a simple method which the stub interpreter can handle? (e.g. returns of
     * constants or throws)
     */
    private fun UMethod.isStub(): Boolean {
      val statement = this.uastBody ?: return true
      if (statement is UBlockExpression) {
        val expressions = statement.expressions
        if (expressions.isEmpty()) {
          return true
        }
        if (expressions.size == 1) {
          val single = expressions[0]
          if (single is UReturnExpression) {
            val returnValue = single.returnExpression
            if (
              returnValue == null ||
                returnValue.isNullLiteral() ||
                ConstantEvaluator.evaluate(null, returnValue) != null
            ) {
              return true
            }
          } else if (single is UThrowExpression) {
            return true
          } else if (single is UCallExpression) {
            if (single.sourcePsi is KtConstructorDelegationCall) {
              return true
            }
            val name = single.methodName
            if (name == "error" || name == "TODO" || name == "fail" || name == "super") {
              return true
            }
          }
        }
      }

      return false
    }

    // See https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html -- section 4.3.4
    // Signatures
    private fun getGenericsSignature(cls: PsiClass): String? {
      val typeParameters = cls.typeParameters
      if (typeParameters.isEmpty()) {
        return null
      }
      val signature = StringBuilder()
      appendFormalTypeParameters(typeParameters, signature)
      appendSuperclassSignature(cls, signature)
      appendSuperinterfaceSignature(cls, signature)
      return signature.toString()
    }

    private fun getGenericsSignature(method: PsiMethod, instanceOuterClass: PsiClass?): String? {
      // MethodTypeSignature:
      //    FormalTypeParameters (TypeSignature*) ReturnType ThrowsSignature*
      //
      // ReturnType:
      //    TypeSignature
      //    VoidDescriptor
      //
      // ThrowsSignature:
      //    ^ ClassTypeSignature
      //    ^ TypeVariableSignature
      val typeParameters = method.typeParameters
      val signature = StringBuilder()
      appendFormalTypeParameters(typeParameters, signature)
      appendMethodTypeSignature(method, signature, instanceOuterClass)
      appendReturnTypeSignature(method, signature)
      appendThrowsSignature(method, signature)
      return if (!signature.haveGenerics()) {
        // It looks like the compiler omits these if there are no generics
        null
      } else {
        signature.toString()
      }
    }

    private fun getGenericsSignature(field: PsiField): String? {
      val signature = StringBuilder()
      appendFieldTypeSignature(field.type, signature)
      return if (!signature.haveGenerics()) {
        // It looks like the compiler omits these if there are no generics
        null
      } else {
        signature.toString()
      }
    }

    private fun StringBuilder.haveGenerics(): Boolean {
      var i = 0
      while (i < length) {
        val c = this[i]
        if (c == '<' || c == 'T') {
          return true
        }
        if (c == 'L') {
          while (i < length) {
            val d = this[i]
            if (d == '<') {
              return true
            } else if (d == ';') {
              break
            } else {
              i++
            }
          }
        }
        i++
      }

      return false
    }

    private fun appendMethodTypeSignature(
      method: PsiMethod,
      signature: StringBuilder,
      instanceOuterClass: PsiClass?
    ) {
      signature.append('(')
      if (instanceOuterClass != null) {
        // Synthetic field to outer class inserted in argument list
        val outerType =
          JavaPsiFacade.getElementFactory(method.project).createType(instanceOuterClass)
        appendFieldTypeSignature(outerType, signature)
      }
      for (parameter in method.parameterList.parameters) {
        appendFieldTypeSignature(parameter.type, signature)
      }
      signature.append(')')
    }

    private fun appendReturnTypeSignature(method: PsiMethod, signature: StringBuilder) {
      val returnType = method.returnType ?: PsiTypes.voidType()
      appendFieldTypeSignature(returnType, signature)
    }

    private fun appendThrowsSignature(method: PsiMethod, signature: StringBuilder) {
      // ThrowsSignature:
      //    ^ ClassTypeSignature
      //    ^ TypeVariableSignature
      for (throwsType in method.throwsList.referencedTypes) {
        signature.append('^')
        appendClassTypeSignature(throwsType, signature)
      }
    }

    private fun appendFormalTypeParameters(
      typeParameters: Array<PsiTypeParameter>,
      signature: StringBuilder
    ) {
      if (typeParameters.isEmpty()) {
        return
      }
      signature.append('<')

      // FormalTypeParameters:
      //    < FormalTypeParameter+ >
      for (type in typeParameters) {
        // FormalTypeParameter:
        //    Identifier ClassBound InterfaceBound*

        // Identifier
        signature.append(type.name)

        //    ClassBound InterfaceBound*
        // ClassBound:
        //    : FieldTypeSignature*
        // InterfaceBound:
        //    : FieldTypeSignature
        val extendsTypes = type.extendsListTypes
        if (extendsTypes.isEmpty()) {
          signature.append(":Ljava/lang/Object;")
        } else {
          var first = true
          for (extendsType in extendsTypes) {
            signature.append(':')
            if (first) {
              if (extendsType.resolve()?.isInterface == true) {
                // This is weird, but I see "T::" for type variables where there isn't a super
                // class, only super
                // interface
                signature.append(':')
              }
              first = false
            }
            appendFieldTypeSignature(extendsType, signature)
          }
        }
      }
      signature.append('>')
    }

    private fun appendSuperclassSignature(cls: PsiClass, signature: StringBuilder) {
      val superType = cls.superTypes.first()
      appendClassTypeSignature(superType, signature)
    }

    private fun appendSuperinterfaceSignature(cls: PsiClass, signature: StringBuilder) {
      val implementsTypes = cls.implementsListTypes
      for (implementedType in implementsTypes) {
        appendFieldTypeSignature(implementedType, signature)
      }
    }

    private fun appendFieldTypeSignature(type: PsiType, signature: StringBuilder) {
      if (type is PsiArrayType) {
        appendArrayTypeSignature(type, signature)
      } else if (type is PsiClassType) {
        if (type.resolve() is PsiTypeParameter) {
          appendTypeVariableSignature(type.resolve() as PsiTypeParameter, signature)
        } else {
          appendClassTypeSignature(type, signature)
        }
      } else if (type is PsiPrimitiveType) {
        signature.append(JavaEvaluator.getPrimitiveSignature(type.canonicalText))
      } else {
        error("Unexpected type $type of class ${type.javaClass}")
      }
    }
    private fun appendClassTypeSignature(type: PsiClassType, signature: StringBuilder) {
      // We can't use type.rawType()canonicalText here because it will flatten
      // inner classes using "." instead of "$" so we can't tell A.B from A$B
      val resolved = type.resolve()
      if (resolved != null) {
        if (resolved is PsiTypeParameter) {
          signature.append('T')
          signature.append(resolved.name)
          return
        } else {
          signature.append('L')
          val qualified = resolved.qualifiedName!!
          var outerMost: PsiClass = resolved
          while (true) {
            val outer = outerMost.containingClass
            if (outer != null) {
              outerMost = outer
            } else {
              break
            }
          }
          if (outerMost == resolved) {
            signature.append(qualified.replace('.', '/'))
          } else {
            val outerQualified = outerMost.qualifiedName!!
            val canonical =
              outerQualified.replace('.', '/') +
                qualified.substring(outerQualified.length).replace('.', '$')
            signature.append(canonical)
          }
        }
      } else {
        // best effort (incomplete class path)
        signature.append('L')
        signature.append(type.rawType().canonicalText.replace('.', '/'))
      }
      val parameters = type.parameters
      if (parameters.isNotEmpty()) {
        signature.append('<')
        for (parameter in parameters) {
          if (parameter is PsiWildcardType) {
            if (parameter.isSuper) {
              signature.append('-')
              appendFieldTypeSignature(parameter.superBound, signature)
            } else if (parameter.isExtends) {
              signature.append('+')
              appendFieldTypeSignature(parameter.extendsBound, signature)
            } else {
              // TODO: What does it take to have "*" ?
              error("Unexpected/unsupported wildcard type $parameter")
            }
          } else {
            signature.append('L').append(parameter.canonicalText.replace('.', '/')).append(';')
          }
        }
        signature.append('>')
      }
      signature.append(';')
    }

    private fun appendArrayTypeSignature(type: PsiArrayType, signature: StringBuilder) {
      signature.append('[')
      appendFieldTypeSignature(type.componentType, signature)
    }

    private fun appendTypeVariableSignature(type: PsiTypeParameter, signature: StringBuilder) {
      signature.append('T')
      signature.append(type.name)
      signature.append(';')
    }
  }
}

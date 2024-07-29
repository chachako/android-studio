/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.lint.uast

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.file.impl.JavaFileManager
import com.intellij.psi.search.GlobalSearchScope
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.kotlin.analysis.api.platform.packages.createPackagePartProvider
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.PROPERTY_GETTER
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget.PROPERTY_SETTER
import org.jetbrains.kotlin.light.classes.symbol.annotations.getJvmNameFromAnnotation
import org.jetbrains.kotlin.light.classes.symbol.annotations.toFilter
import org.jetbrains.kotlin.light.classes.symbol.annotations.toOptionalFilter
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.jvm.KotlinCliJavaFileManager
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeSmart

private class KotlinStaticPsiDeclarationFromBinaryModuleProvider(
  private val project: Project,
  val scope: GlobalSearchScope,
  private val packagePartProvider: PackagePartProvider,
) : KotlinPsiDeclarationProvider() {

  private val javaFileManager by lazyPub { project.getService(JavaFileManager::class.java) }

  private val classesInPackageCache = ConcurrentHashMap<FqName, Collection<PsiClass>>()

  private fun getClassesInPackage(fqName: FqName): Collection<PsiClass> {
    return classesInPackageCache.getOrPut(fqName) {
      // `javaFileManager.findPackage(fqName).classes` triggers reading decompiled text from stub
      // for built-in,
      // which will fail since such stubs are fake, i.e., no mirror to render decompiled text.
      // Instead, we will find/use potential class names in the package, while considering package
      // parts.
      val packageParts =
        packagePartProvider.findPackageParts(fqName.asString()).map { it.replace("/", ".") }
      val fqNames =
        packageParts.ifEmpty {
          (javaFileManager as? KotlinCliJavaFileManager)?.knownClassNamesInPackage(fqName)?.map {
            name ->
            fqName.child(Name.identifier(name)).asString()
          }
        } ?: return@getOrPut emptyList()
      fqNames
        .flatMap { fqName -> javaFileManager.findClasses(fqName, scope).asIterable() }
        .distinct()
    }
  }

  override fun getClassesByClassId(classId: ClassId): Collection<PsiClass> {
    JavaToKotlinClassMap.mapKotlinToJava(classId.asSingleFqName().toUnsafe())?.let {
      return getClassesByClassId(it)
    }

    classId.parentClassId?.let { parentClassId ->
      val innerClassName = classId.relativeClassName.asString().split(".").last()
      return getClassesByClassId(parentClassId).mapNotNull { parentClsClass ->
        parentClsClass.innerClasses.find { it.name == innerClassName }
      }
    }
    return listOfNotNull(javaFileManager.findClass(classId.asFqNameString(), scope))
  }

  override fun getProperties(variableLikeSymbol: KaVariableLikeSymbol): Collection<PsiMember> {
    val callableId = variableLikeSymbol.callableId ?: return emptyList()
    val classes =
      callableId.classId?.let { classId ->
        val classFromCurrentClassId = getClassesByClassId(classId)
        // property in companion object is actually materialized at the containing class.
        val classFromOuterClassID =
          classId.outerClassId?.let { getClassesByClassId(it) } ?: emptyList()
        classFromCurrentClassId + classFromOuterClassID
      } ?: getClassesInPackage(callableId.packageName)
    if (classes.isEmpty()) return emptyList()

    val propertySymbol = variableLikeSymbol as? KtPropertySymbol
    val getterJvmName =
      propertySymbol?.getter?.getJvmNameFromAnnotation(PROPERTY_GETTER.toOptionalFilter())
        ?: propertySymbol?.getJvmNameFromAnnotation(PROPERTY_GETTER.toFilter())
    val setterJvmName =
      propertySymbol?.setter?.getJvmNameFromAnnotation(PROPERTY_SETTER.toOptionalFilter())
        ?: propertySymbol?.getJvmNameFromAnnotation(PROPERTY_SETTER.toFilter())
    return classes
      .flatMap { psiClass ->
        psiClass.children.filterIsInstance<PsiMember>().filter { psiMember ->
          if (psiMember !is PsiMethod && psiMember !is PsiField) return@filter false
          val name = psiMember.name ?: return@filter false
          val id = callableId.callableName.identifier
          // PsiField a.k.a. backing field
          if (name == id) return@filter true
          // PsiMethod, i.e., accessors
          if (name == getterJvmName || name == setterJvmName) return@filter true
          val nameWithoutPrefix = name.nameWithoutAccessorPrefix ?: return@filter false
          // E.g., getJVM_FIELD -> JVM_FIELD
          nameWithoutPrefix == id ||
            // E.g., getFooBar -> FooBar -> fooBar
            nameWithoutPrefix.decapitalizeSmart().let { decapitalizedPrefix ->
              decapitalizedPrefix.endsWith(id) ||
                // value class mangling: getColor-hash
                isValueClassMangled(decapitalizedPrefix, id)
            }
        }
      }
      .toList()
  }

  private val String.nameWithoutAccessorPrefix: String?
    get() =
      when {
        this.startsWith("get") || this.startsWith("set") -> substring(3)
        this.startsWith("is") -> substring(2)
        else -> null
      }

  override fun getFunctions(functionLikeSymbol: KaFunctionLikeSymbol): Collection<PsiMethod> {
    val callableId = functionLikeSymbol.callableId ?: return emptyList()
    val classes =
      callableId.classId?.let { classId -> getClassesByClassId(classId) }
        ?: getClassesInPackage(callableId.packageName)
    if (classes.isEmpty()) return emptyList()

    val jvmName =
      when (functionLikeSymbol) {
        is KtPropertyGetterSymbol -> {
          functionLikeSymbol.getJvmNameFromAnnotation(PROPERTY_GETTER.toOptionalFilter())
        }
        is KtPropertySetterSymbol -> {
          functionLikeSymbol.getJvmNameFromAnnotation(PROPERTY_SETTER.toOptionalFilter())
        }
        else -> {
          functionLikeSymbol.getJvmNameFromAnnotation()
        }
      }
    val id = jvmName ?: callableId.callableName.identifier
    return classes
      .flatMap { psiClass ->
        psiClass.methods.filter { psiMethod ->
          psiMethod.name == id ||
            // value class mangling: functionName-hash
            isValueClassMangled(psiMethod.name, id)
        }
      }
      .toList()
  }

  private fun isValueClassMangled(name: String, prefix: String): Boolean {
    // A memory optimization for `name.startsWith("$prefix-")`, see KT-63486
    return name.length > prefix.length && name[prefix.length] == '-' && name.startsWith(prefix)
  }
}

internal class KotlinStaticPsiDeclarationProviderFactory(private val project: Project) :
  KotlinPsiDeclarationProviderFactory() {
  // TODO: For now, [createPsiDeclarationProvider] is always called with the project scope, hence
  // singleton.
  //  If we come up with a better / optimal search scope, we may need a different way to cache
  // scope-to-provider mapping.
  private val provider: KotlinStaticPsiDeclarationFromBinaryModuleProvider by lazyPub {
    val searchScope = GlobalSearchScope.allScope(project)
    KotlinStaticPsiDeclarationFromBinaryModuleProvider(
      project,
      searchScope,
      project.createPackagePartProvider(searchScope),
    )
  }

  override fun createPsiDeclarationProvider(
    searchScope: GlobalSearchScope
  ): KotlinPsiDeclarationProvider {
    return if (searchScope == provider.scope) {
      provider
    } else {
      KotlinStaticPsiDeclarationFromBinaryModuleProvider(
        project,
        searchScope,
        project.createPackagePartProvider(searchScope),
      )
    }
  }
}

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

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.util.indexing.FileContentImpl
import org.jetbrains.kotlin.analysis.decompiler.konan.K2KotlinNativeMetadataDecompiler
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.stubs.impl.KotlinClassStubImpl
import org.jetbrains.kotlin.psi.stubs.impl.KotlinFileStubImpl
import org.jetbrains.kotlin.psi.stubs.impl.KotlinObjectStubImpl
import org.jetbrains.kotlin.psi.stubs.impl.KotlinStubBaseImpl

internal fun klibMetaFiles(root: VirtualFile): Collection<VirtualFile> {
  return buildList {
    VfsUtilCore.visitChildrenRecursively(
      root,
      object : VirtualFileVisitor<Void>() {
        override fun visitFile(file: VirtualFile): Boolean {
          if (file.fileType == KlibMetaFileType) {
            add(file)
          }
          return true
        }
      },
    )
  }
}

internal fun buildStubByVirtualFile(file: VirtualFile): KotlinFileStubImpl? {
  val fileContent = FileContentImpl.createByFile(file)
  return K2KotlinNativeMetadataDecompiler().stubBuilder.buildFileStub(fileContent)
    as? KotlinFileStubImpl
}

internal fun buildPsiClassByKotlinClassStub(
  psiManager: PsiManager,
  ktFile: KtFile,
  ktStub: KotlinStubBaseImpl<*>,
): PsiClass? {
  return when (ktStub) {
    is KotlinClassStubImpl -> {
      val ktClass = ktStub.psi
      LintFakeLightClassForKlib(ktClass, psiManager, ktClass.name.orAnonymous(ktClass), ktFile)
    }
    is KotlinObjectStubImpl -> {
      val ktObject = ktStub.psi
      LintFakeLightClassForKlib(ktObject, psiManager, ktObject.name.orAnonymous(ktObject), ktFile)
    }
    else -> null
  }
}

internal fun String?.orAnonymous(ktDeclaration: KtNamedDeclaration): String {
  if (this != null) return this
  return when (ktDeclaration) {
    is KtEnumEntry -> "<anonymous enum entry>"
    is KtClass -> "<anonymous class>"
    is KtObjectDeclaration -> "<anonymous object>"
    is KtNamedFunction -> "<anonymous function>"
    is KtProperty -> "<anonymous property>"
    else -> "<unknown ${ktDeclaration::class}>"
  }
}

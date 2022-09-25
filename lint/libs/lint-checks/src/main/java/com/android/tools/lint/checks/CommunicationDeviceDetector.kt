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
package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.uast.UCallExpression
import java.util.EnumSet

class CommunicationDeviceDetector : Detector(), SourceCodeScanner {
    companion object {
        private val IMPLEMENTATION = Implementation(
            CommunicationDeviceDetector::class.java,
            EnumSet.of(Scope.ALL_JAVA_FILES)
        )

        /** Calling `setCommunicationDevice()` without `clearCommunicationDevice()` */
        @JvmField
        val ISSUE = Issue.create(
            id = "SetAndClearCommunicationDevice",
            briefDescription = "Clearing communication device",
            explanation = """
                After selecting the audio device for communication use cases using `setCommunicationDevice(AudioDeviceInfo device)`, the \
                selection is active as long as the requesting application process lives, until `clearCommunicationDevice()` is called or \
                until the device is disconnected. It is therefore important to clear the request when a call ends or the requesting activity \
                or service is stopped or destroyed.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
            androidSpecific = true
        )

        const val FOUND_CLEARCOMMUNICATIONDEVICE = "foundClearCommunicationDevice"
        const val MIN_TARGET_SDK = 31
    }

    // Number of calls to setCommunicationDevice() in the current project
    private var numSetCalls = 0

    override fun getApplicableMethodNames() = listOf("setCommunicationDevice", "clearCommunicationDevice")

    override fun afterCheckRootProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            checkPartialResults(context, context.getPartialResults(ISSUE))
        }
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        val map = context.getPartialResults(ISSUE).map()
        if (method.getFqName() == "android.media.AudioManager.setCommunicationDevice") {
            if (!context.driver.isSuppressed(context, ISSUE, node)) {
                map.put(numSetCalls++.toString(), context.getLocation(node))
            }
        } else if (method.getFqName() == "android.media.AudioManager.clearCommunicationDevice") {
            map.put(FOUND_CLEARCOMMUNICATIONDEVICE, true)
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        if (context.mainProject.targetSdk < MIN_TARGET_SDK) return

        val foundClearCommunicationDevice = partialResults.maps().any { map ->
            map.keys().any { it == FOUND_CLEARCOMMUNICATIONDEVICE }
        }

        if (!foundClearCommunicationDevice) {
            for (map in partialResults.maps()) {
                map.forEach {
                    if (it != FOUND_CLEARCOMMUNICATIONDEVICE) {
                        context.report(
                            Incident(context)
                                .issue(ISSUE)
                                .location(map.getLocation(it)!!)
                                .message("Must call `clearCommunicationDevice()` after `setCommunicationDevice()`")
                        )
                    }
                }
            }
        }
    }
}

fun PsiElement.getFqName(): String? = when (val element = namedUnwrappedElement) {
    is PsiMember -> element.getName()?.let { name ->
        val prefix = element.containingClass?.qualifiedName
        (if (prefix != null) "$prefix.$name" else name)
    }

    is KtNamedDeclaration -> element.fqName.toString()
    else -> null
}

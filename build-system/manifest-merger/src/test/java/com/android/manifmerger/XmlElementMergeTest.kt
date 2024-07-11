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

package com.android.manifmerger

import com.android.manifmerger.ManifestMerger2.ProcessCancellationChecker
import com.android.testutils.MockitoKt.whenever
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.argThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.Optional

class XmlElementMergeTest {
    private val processCancellationChecker = mock(ProcessCancellationChecker::class.java)
    private val mergingReport = mock(MergingReport.Builder::class.java)

    @Test
    fun testUnMatchedKeys() {
        val lowPriorityNode = rootElement {
            key = "lowPriorityNode"
            childElement {
                key = "lpChild1"
            }
            childElement {
                key = "lpChild2"
            }
        }.toElement()
        val highPriorityNode = rootElement {
            key = "highPriorityNode"
            childElement {
                key = "hpChild1"
            }
        }.toElement()
        val pairs = mapMergingElements(lowPriorityNode, highPriorityNode, processCancellationChecker, mergingReport)
        assertThat(pairs).containsExactly(
            Pair(lowPriorityNode.mergeableElements[0], Optional.empty<XmlElement>()),
            Pair(lowPriorityNode.mergeableElements[1], Optional.empty<XmlElement>()))
    }

    @Test
    fun testAbsentKeyNoMatchingChild() {
        val lowPriorityNode = rootElement {
            key = "lowPriorityNode"
            // Two child elements with null key.
            childElement {
            }
            childElement {
            }
        }.toElement()
        val highPriorityNode = rootElement {
            key = "highPriorityNode"
            childElement {
                key = "hpChild1"
            }
        }.toElement()
        val firstNodeByType = highPriorityNode.mergeableElements[0]
        whenever(highPriorityNode.getFirstNodeByType(any())).thenReturn(Optional.of(firstNodeByType))
        val pairs = mapMergingElements(lowPriorityNode, highPriorityNode, processCancellationChecker, mergingReport)
        assertThat(pairs).containsExactly(
            Pair(lowPriorityNode.mergeableElements[0], Optional.of(highPriorityNode.mergeableElements[0])),
            Pair(lowPriorityNode.mergeableElements[1], Optional.of(highPriorityNode.mergeableElements[0])))
    }

    @Test
    fun testMultipleDeclarationAllowed() {
        val lowPriorityNode = rootElement {
            key = "lowPriorityNode"
            childElement {
                key = "child1"
                multipleDeclarationAllowed = true
            }
            childElement {
                key = "child1"
                multipleDeclarationAllowed = true
            }
            childElement {
                key = "child1"
                featureFlag = "featureFlag1"
                multipleDeclarationAllowed = true
            }
        }.toElement()
        val highPriorityNode = rootElement {
            key = "highPriorityNode"
            childElement {
                key = "child1"
                multipleDeclarationAllowed = true
            }
        }.toElement()
        val pairs = mapMergingElements(lowPriorityNode, highPriorityNode, processCancellationChecker, mergingReport)
        assertThat(pairs).containsExactly(
            Pair(lowPriorityNode.mergeableElements[0], Optional.of(highPriorityNode.mergeableElements[0])),
            Pair(lowPriorityNode.mergeableElements[1], Optional.of(highPriorityNode.mergeableElements[0])),
            Pair(lowPriorityNode.mergeableElements[2], Optional.of(highPriorityNode.mergeableElements[0])))
    }

    @Test
    fun testLowPriorityWithFeatureFlagHighPriorityNodeWithFeatureFlag() {
        val lowPriorityNode = rootElement {
            key = "lowPriorityNode"
            childElement {
                key = "child1"
                featureFlag = "featureFlag1"
            }
            childElement {
                key = "child1"
                featureFlag = "featureFlag1"
            }
            childElement {
                key = "child1"
                featureFlag = "featureFlag1"
            }
        }.toElement()
        val highPriorityNode = rootElement {
            key = "highPriorityNode"
            childElement {
                key = "child1"
                featureFlag = "!featureFlag1"
            }
            childElement {
                key = "child1"
                featureFlag = "featureFlag1"
            }
            childElement {
                key = "child1"
                featureFlag = "featureFlag2"
            }
        }.toElement()
        val pairs = mapMergingElements(lowPriorityNode, highPriorityNode, processCancellationChecker, mergingReport)
        assertThat(pairs).containsExactly(
            Pair(lowPriorityNode.mergeableElements[0], Optional.of(highPriorityNode.mergeableElements[1])),
            Pair(lowPriorityNode.mergeableElements[1], Optional.of(highPriorityNode.mergeableElements[1])),
            Pair(lowPriorityNode.mergeableElements[2], Optional.of(highPriorityNode.mergeableElements[1])))
    }

    @Test
    fun testLowPriorityWithFeatureFlagHighPriorityNodeWithAndWithoutFeatureFlag() {
        val lowPriorityNode = rootElement {
            key = "lowPriorityNode"
            childElement {
                key = "child1"
                featureFlag = "featureFlag1"
            }
            childElement {
                key = "child1"
                featureFlag = "featureFlag1"
            }
            childElement {
                key = "child1"
                featureFlag = "featureFlag1"
            }
        }.toElement()
        val highPriorityNode = rootElement {
            key = "highPriorityNode"
            childElement {
                key = "child1"
                featureFlag = "featureFlag1"
            }
            childElement {
                key = "child1"
            }
        }.toElement()
        val pairs = mapMergingElements(lowPriorityNode, highPriorityNode, processCancellationChecker, mergingReport)
        assertThat(pairs).containsExactly(
            Pair(lowPriorityNode.mergeableElements[0], Optional.of(highPriorityNode.mergeableElements[0])),
            Pair(lowPriorityNode.mergeableElements[1], Optional.of(highPriorityNode.mergeableElements[0])),
            Pair(lowPriorityNode.mergeableElements[2], Optional.of(highPriorityNode.mergeableElements[0])))
    }

    @Test
    fun testLowPriorityWithFeatureFlagHighPriorityNodeWithoutFeatureFlag() {
        val lowPriorityNode = rootElement {
            key = "lowPriorityNode"
            childElement {
                key = "child1"
                featureFlag = "featureFlag1"
            }
            childElement {
                key = "child1"
                featureFlag = "featureFlag1"
            }
            childElement {
                key = "child1"
                featureFlag = "featureFlag1"
            }
        }.toElement()
        val highPriorityNode = rootElement {
            key = "highPriorityNode"
            childElement {
                key = "child1"
            }
        }.toElement()
        val pairs = mapMergingElements(lowPriorityNode, highPriorityNode, processCancellationChecker, mergingReport)
        val addMessageFirstArgumentCaptor = ArgumentCaptor.forClass(XmlElement::class.java)
        assertThat(pairs).isEmpty()
        verify(mergingReport, times(3))
            .addMessage(
                addMessageFirstArgumentCaptor.capture(),
                argThat { it == MergingReport.Record.Severity.ERROR },
                argThat { it.contains("Cannot merge element") })
        assertThat(addMessageFirstArgumentCaptor.allValues.distinct()).containsExactly(highPriorityNode.mergeableElements[0])
    }

    @Test
    fun testLowPriorityWithoutFeatureFlagHighPriorityNodeWithoutFeatureFlag() {
        val lowPriorityNode = rootElement {
            key = "lowPriorityNode"
            childElement {
                key = "child1"
            }
            childElement {
                key = "child2"
            }
        }.toElement()
        val highPriorityNode = rootElement {
            key = "highPriorityNode"
            childElement {
                key = "child1"
            }
            childElement {
                key = "child2"
            }
            childElement {
                key = "child1"
                featureFlag = "experiment1"
            }
            childElement {
                key = "child1"
                featureFlag = "experiment2"
            }
        }.toElement()
        val pairs = mapMergingElements(lowPriorityNode, highPriorityNode, processCancellationChecker, mergingReport)
        assertThat(pairs).containsExactly(
            Pair(lowPriorityNode.mergeableElements[0], Optional.of(highPriorityNode.mergeableElements[0])),
            Pair(lowPriorityNode.mergeableElements[1], Optional.of(highPriorityNode.mergeableElements[1]))
            )
    }

    @Test
    fun testLowPriorityWithoutFeatureFlagHighPriorityNodeWithFeatureFlag() {
        val lowPriorityNode = rootElement {
            key = "lowPriorityNode"
            childElement {
                key = "child1"
            }
            childElement {
                key = "child2"
            }
        }.toElement()
        val highPriorityNode = rootElement {
            key = "highPriorityNode"
            childElement {
                key = "child1"
                featureFlag = "experiment1"
            }
            childElement {
                key = "child1"
                featureFlag = "!experiment1"
            }
            childElement {
                key = "child2"
                featureFlag = "experiment1"
            }
            childElement {
                key = "child2"
                featureFlag = "experiment2"
            }
        }.toElement()
        val clonedLowPriorityNodes = listOf(mock(XmlElement::class.java), mock(XmlElement::class.java),
            mock(XmlElement::class.java), mock(XmlElement::class.java))
        var index = 0
        for (i in 0..1) {
            val node = lowPriorityNode.mergeableElements[i]
            whenever(node.clone())
                .thenReturn(clonedLowPriorityNodes[index++])
                .thenReturn(clonedLowPriorityNodes[index++])
        }
        val pairs = mapMergingElements(lowPriorityNode, highPriorityNode, processCancellationChecker, mergingReport)
        assertThat(pairs).containsExactly(
            Pair(clonedLowPriorityNodes[0], Optional.of(highPriorityNode.mergeableElements[0])),
            Pair(clonedLowPriorityNodes[1], Optional.of(highPriorityNode.mergeableElements[1])),
            Pair(clonedLowPriorityNodes[2], Optional.of(highPriorityNode.mergeableElements[2])),
            Pair(clonedLowPriorityNodes[3], Optional.of(highPriorityNode.mergeableElements[3]))
        )
        verify(clonedLowPriorityNodes[0]).setFeatureFlag(highPriorityNode.mergeableElements[0].featureFlag()!!.attributeValue)
        verify(clonedLowPriorityNodes[1]).setFeatureFlag(highPriorityNode.mergeableElements[1].featureFlag()!!.attributeValue)
        verify(clonedLowPriorityNodes[2]).setFeatureFlag(highPriorityNode.mergeableElements[2].featureFlag()!!.attributeValue)
        verify(clonedLowPriorityNodes[3]).setFeatureFlag(highPriorityNode.mergeableElements[3].featureFlag()!!.attributeValue)
    }
}

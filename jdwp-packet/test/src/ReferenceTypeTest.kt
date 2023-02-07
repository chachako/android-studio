import com.android.jdwppacket.SourceFileCmd
import com.android.jdwppacket.SourceFileReply
import com.android.jdwppacket.referencetype.MethodsCmd
import com.android.jdwppacket.referencetype.MethodsReply
import com.android.jdwppacket.referencetype.MethodsWithGenericsCmd
import com.android.jdwppacket.referencetype.MethodsWithGenericsReply
import com.android.jdwppacket.referencetype.SignatureCmd
import com.android.jdwppacket.referencetype.SignatureReply
import com.android.jdwppacket.referencetype.SignatureWithGenericCmd
import com.android.jdwppacket.referencetype.SignatureWithGenericReply
import com.android.jdwppacket.referencetype.SourceDebugExtensionCmd
import com.android.jdwppacket.referencetype.SourceDebugExtensionReply
import org.junit.Test

/*
 * Copyright (C) 2023 The Android Open Source Project
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
class ReferenceTypeTest {

  @Test
  fun testMethodsCmd() {
    val packet = MethodsCmd(Long.MAX_VALUE)
    assertJDWPObjectAndWireEquals(packet, MethodsCmd::parse)
  }

  @Test
  fun testMethodsReply() {
    val methods = listOf(MethodsReply.Method(Long.MAX_VALUE, "foo", "bar", Integer.MAX_VALUE))
    val packet = MethodsReply(methods)
    assertJDWPObjectAndWireEquals(packet, MethodsReply::parse)
  }

  @Test
  fun testMethodsWithGenericCmd() {
    val packet = MethodsWithGenericsCmd(Long.MAX_VALUE)
    assertJDWPObjectAndWireEquals(packet, MethodsWithGenericsCmd::parse)
  }

  @Test
  fun testMethodsWithGenericReply() {
    val methods =
      listOf(
        MethodsWithGenericsReply.Method(Long.MAX_VALUE, "foo", "bar", "baz", Integer.MAX_VALUE)
      )
    val packet = MethodsWithGenericsReply(methods)
    assertJDWPObjectAndWireEquals(packet, MethodsWithGenericsReply::parse)
  }

  @Test
  fun testSignatureCmd() {
    val packet = SignatureCmd(Long.MAX_VALUE)
    assertJDWPObjectAndWireEquals(packet, SignatureCmd::parse)
  }

  @Test
  fun testSignatureReply() {
    val packet = SignatureReply("some signature")
    assertJDWPObjectAndWireEquals(packet, SignatureReply::parse)
  }

  @Test
  fun testSignatureWithGenericCmd() {
    val packet = SignatureWithGenericCmd(Long.MAX_VALUE)
    assertJDWPObjectAndWireEquals(packet, SignatureWithGenericCmd::parse)
  }

  @Test
  fun testSignatureWithGeneticReply() {
    val packet = SignatureWithGenericReply("some signature", "another signature")
    assertJDWPObjectAndWireEquals(packet, SignatureWithGenericReply::parse)
  }

  @Test
  fun testSourceDebugExtensionCmd() {
    val packet = SourceDebugExtensionCmd(Long.MAX_VALUE)
    assertJDWPObjectAndWireEquals(packet, SourceDebugExtensionCmd::parse)
  }

  @Test
  fun testSourceDebugExtensionReply() {
    val packet = SourceDebugExtensionReply("some extension")
    assertJDWPObjectAndWireEquals(packet, SourceDebugExtensionReply::parse)
  }

  @Test
  fun testSourceFileCmd() {
    val packet = SourceFileCmd(Long.MAX_VALUE)
    assertJDWPObjectAndWireEquals(packet, SourceFileCmd::parse)
  }

  @Test
  fun testSourceFileReply() {
    val packet = SourceFileReply("/foo/bar")
    assertJDWPObjectAndWireEquals(packet, SourceFileReply::parse)
  }
}

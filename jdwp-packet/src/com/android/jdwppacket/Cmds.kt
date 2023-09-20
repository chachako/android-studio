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
package com.android.jdwppacket

enum class VirtualMachine(val id: Int) : CmdType {
  Version(1),
  ClassesBySignature(2),
  AllClasses(3),
  AllThreads(4),
  TopLevelThreadGroups(5),
  Dispose(6),
  IDSizes(7),
  Suspend(8),
  Resume(9),
  Exit(10),
  CreateString(11),
  Capabilities(12),
  ClassPaths(13),
  DisposeObjects(14),
  HoldEvents(15),
  ReleaseEvents(16),
  CapabilitiesNew(17),
  RedefineClasses(18),
  SetDefaultStratum(19),
  AllClassesWithGeneric(20),
  InstanceCounts(21);

  override val ID = id
  override val setID = CmdSet.Vm.id
}

enum class ReferenceType(val id: Int) : CmdType {
  Signature(1),
  ClassLoader(2),
  Modifiers(3),
  Fields(4),
  Methods(5),
  GetValues(6),
  SourceFile(7),
  NestedTypes(8),
  Status(9),
  Interfaces(10),
  ClassObject(11),
  SourceDebugExtension(12),
  SignatureWithGeneric(13),
  FieldsWithGeneric(14),
  MethodsWithGeneric(15),
  Instances(16),
  ClassFileVersion(17),
  ConstantPool(18);

  override val ID = id
  override val setID = CmdSet.ReferenceType.id
}

enum class ClassType(val id: Int) : CmdType {
  Superclass(1),
  SetValues(2),
  InvokeMethod(3),
  NewInstance(4);

  override val ID = id
  override val setID = CmdSet.ClassType.id
}

enum class ArrayType(val id: Int) : CmdType {
  NewInstance(1);

  override val ID = id
  override val setID = CmdSet.ArrayType.id
}

enum class InterfaceType(val id: Int) : CmdType {
  InvokeMethod(1);

  override val ID = id
  override val setID = CmdSet.InterfaceType.id
}

enum class Method(val id: Int) : CmdType {
  LineTable(1),
  VariableTable(2),
  Bytecodes(3),
  IsObsolete(4),
  VariableTableWithGeneric(5);

  override val ID = id
  override val setID = CmdSet.Method.id
}

enum class Field(val id: Int) : CmdType {
  UNUSED(0);

  override val ID = id
  override val setID = CmdSet.Field.id
}

enum class ObjectReference(val id: Int) : CmdType {
  ReferenceType(1),
  GetValues(2),
  SetValues(3),
  MonitorInfo(5),
  InvokeMethod(6),
  DisableCollection(7),
  EnableCollection(8),
  IsCollected(9),
  ReferringObjects(10);

  override val ID = id
  override val setID = CmdSet.ObjectReference.id
}

enum class StringReference(val id: Int) : CmdType {
  Value(1);

  override val ID = id
  override val setID = CmdSet.StringReference.id
}

// Cmd for set
enum class ThreadReference(val id: Int) : CmdType {
  Name(1),
  Suspend(2),
  Resume(3),
  Status(4),
  ThreadGroup(5),
  Frames(6),
  FrameCount(7),
  OwnedMonitors(8),
  CurrentContendedMonitor(9),
  Stop(10),
  Interrupt(11),
  SuspendCount(12),
  OwnedMonitorsStackDepthInfo(13),
  ForceEarlyReturn(14);

  override val ID = id
  override val setID = CmdSet.ThreadReference.id
}

enum class ThreadGroupReference(val id: Int) : CmdType {
  Name(1),
  Parent(2),
  Children(3);

  override val ID = id
  override val setID = CmdSet.ThreadGroupReference.id
}

enum class ArrayReference(val id: Int) : CmdType {
  Length(1),
  GetValues(2),
  SetValues(3);

  override val ID = id
  override val setID = CmdSet.ArrayReference.id
}

enum class ClassLoaderReference(val id: Int) : CmdType {
  VisibleClasses(1);

  override val ID = id
  override val setID = CmdSet.ClassLoaderReference.id
}

enum class EventRequest(val id: Int) : CmdType {
  Set(1),
  Clear(2),
  ClearAllBreakpoints(3);

  override val ID = id
  override val setID = CmdSet.EventRequest.id
}

enum class StackFrame(val id: Int) : CmdType {
  GetValues(1),
  SetValues(2),
  ThisObject(3),
  PopFrames(4);

  override val ID = id
  override val setID = CmdSet.StackFrame.id
}

enum class ClassObjectReference(val id: Int) : CmdType {
  ReflectedType(1);

  override val ID = id
  override val setID = CmdSet.ClassObjectReference.id
}

enum class Event(val id: Int) : CmdType {
  Composite(1);

  override val ID = id
  override val setID = CmdSet.Event.id
}

/*
 * Copyright (C) 2008 Google Inc.
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

package com.android.tools.perflib.heap;

import com.android.annotations.NonNull;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Collection;
import java.util.function.Function;

import com.google.common.collect.*;

public class Heap {

    private final int mId;

    @NonNull
    private final String mName;

    //  List of threads
    @NonNull
    Int2ObjectMap<ThreadObj> mThreads = new Int2ObjectOpenHashMap<>();

    //  Class definitions
    @NonNull
    Long2ObjectMap<ClassObj> mClassesById = new Long2ObjectOpenHashMap<>();

    @NonNull Multimap<String, ClassObj> mClassesByName = ArrayListMultimap.create();

    //  List of instances of above class definitions
    private final Long2ObjectMap<Instance> mInstances = new Long2ObjectOpenHashMap<>();

    //  The snapshot that this heap is part of
    Snapshot mSnapshot;

    public Heap(int id, @NonNull String name) {
        mId = id;
        mName = name;
    }

    public int getId() {
        return mId;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    public final void addThread(ThreadObj thread, int serialNumber) {
        mThreads.put(serialNumber, thread);
    }

    public final ThreadObj getThread(int serialNumber) {
        return mThreads.get(serialNumber);
    }

    public final void addInstance(long id, Instance instance) {
        mInstances.put(id, instance);
    }

    public final Instance getInstance(long id) {
        return mInstances.get(id);
    }

    public final void addClass(long id, @NonNull ClassObj theClass) {
        mClassesById.put(id, theClass);
        mClassesByName.put(theClass.getClassName(), theClass);
    }

    public final ClassObj getClass(long id) {
        return mClassesById.get(id);
    }

    public final ClassObj getClass(String name) {
        Collection<ClassObj> classes = mClassesByName.get(name);
        if (classes.size() == 1) {
            return classes.iterator().next();
        }
        return null;
    }

    public final Collection<ClassObj> getClasses(String name) {
        return mClassesByName.get(name);
    }

    public final void dumpInstanceCounts() {
        for (ClassObj theClass : mClassesById.values()) {
            int count = theClass.getInstanceCount();

            if (count > 0) {
                System.out.println(theClass + ": " + count);
            }
        }
    }

    public final void dumpSubclasses() {
        for (ClassObj theClass : mClassesById.values()) {
            int count = theClass.getSubclasses().size();

            if (count > 0) {
                System.out.println(theClass);
                theClass.dumpSubclasses();
            }
        }
    }

    public final void dumpSizes() {
        for (ClassObj theClass : mClassesById.values()) {
            int size = 0;

            for (Instance instance : theClass.getHeapInstances(getId())) {
                size += instance.getCompositeSize();
            }

            if (size > 0) {
                System.out.println(theClass + ": base " + theClass.getSize()
                        + ", composite " + size);
            }
        }
    }

    @NonNull
    public Collection<ClassObj> getClasses() {
        return mClassesByName.values();
    }

    public void forEachInstance(@NonNull Function<Instance, Boolean> procedure) {
        for (Instance instance : mInstances.values()) {
            if (!procedure.apply(instance)) {
                return;
            }
        }
    }

    public int getInstancesCount() {
        return mInstances.size();
    }
}

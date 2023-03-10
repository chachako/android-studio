/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.99 (the "License");
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

package com.android.tools.deploy.liveedit;

import com.android.deploy.asm.Type;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.stream.Collectors;

class LiveEditClass {
    // The context this class is defined in.
    private final LiveEditContext context;
    private Interpretable bytecode;

    // Proxy class that can be instantiated to represent an instance of the class defined by the
    // bytecode.
    private Class<?> proxyClass;

    // Whether or not <clinit> has been interpreted and run for this class. Only meaningful if
    // proxyClass is non-null.
    private boolean isInitialized;

    // Static fields of this class. Only populated and used if proxyClass is non-null.
    private final HashMap<String, Object> staticFields;

    // Memoized reflected data so we can avoid repeated iteration over class methods/fields. Only
    // populated and used if proxyClass is non-null.
    private final HashSet<Type> castableTypes;
    private final HashMap<String, Field> superReflectedFields;
    private final HashMap<String, Method> superReflectedMethods;

    public LiveEditClass(LiveEditContext context, Interpretable bytecode, boolean isProxyClass) {
        this.context = context;
        this.staticFields = new HashMap<>();
        this.castableTypes = new HashSet<>();
        this.superReflectedFields = new HashMap<>();
        this.superReflectedMethods = new HashMap<>();
        updateBytecode(bytecode, isProxyClass);
    }

    public void updateBytecode(Interpretable bytecode, boolean isProxyClass) {
        if (isProxyClass) {
            try {
                // Initializes proxyClass, castableTypes, superReflectedFields, and
                // superReflectedMethods.
                setUpProxyClass(bytecode);
            } catch (Exception e) {
                throw new LiveEditException("Could not set up proxy class", e);
            }

            isInitialized = false;
            staticFields.clear();
        }
        this.bytecode = bytecode;
    }

    public boolean isProxyClass() {
        return proxyClass != null;
    }

    public boolean declaresMethod(String methodName, String methodDesc) {
        return bytecode.getMethod(methodName, methodDesc) != null;
    }

    public Method getSuperMethod(String methodName, String methodDesc) {
        return superReflectedMethods.get(methodName + methodDesc);
    }

    public Field getSuperField(String fieldName) {
        return superReflectedFields.get(fieldName);
    }

    public boolean isInstanceOf(Type type) {
        return castableTypes.contains(type);
    }

    public ClassLoader getClassLoader() {
        return context.getClassLoader();
    }

    // Invoke the specified method with the specified receiver and arguments. The method must be
    // declared in the bytecode for this class; methods of superclasses will not be resolved.
    public Object invokeDeclaredMethod(
            String methodName, String methodDesc, Object thisObject, Object[] arguments) {
        MethodBodyEvaluator evaluator =
                new MethodBodyEvaluator(context, bytecode, methodName, methodDesc);
        return evaluator.eval(thisObject, bytecode.getInternalName(), arguments);
    }

    // Returns a new proxy instance that implements all the proxied class' interfaces. Can only be
    // called if the LiveEditClass represents a proxiable class.
    public Object getProxy() {
        InterpreterLogger.v("New proxy: " + getClassInternalName());
        if (proxyClass == null) {
            throw new LiveEditException(
                    "Cannot create a proxy object for a non-proxy LiveEdit class");
        }

        ProxyClassHandler handler = new ProxyClassHandler(this, bytecode.getDefaultFieldValues());
        try {
            return proxyClass
                    .getConstructor(new Class<?>[] {InvocationHandler.class})
                    .newInstance(new Object[] {handler});
        } catch (Exception e) {
            throw new LiveEditException("Could not create proxy object", e);
        }
    }

    public synchronized Object getStaticField(String fieldName) {
        ensureClinit();
        return staticFields.get(fieldName);
    }

    public synchronized void setStaticField(String fieldName, Object value) {
        ensureClinit();
        staticFields.put(fieldName, value);
    }

    private synchronized void ensureClinit() {
        if (proxyClass == null) {
            throw new LiveEditException("Cannot invoke <clinit> for non-proxy LiveEdit class");
        }
        if (!isInitialized) {
            isInitialized = true; // Must set this first to prevent infinite loops.
            invokeDeclaredMethod("<clinit>", "()V", null, new Object[0]);
        }
    }

    public RiskyChange checkForRiskyChange(Interpretable bytecode) {
        if (this.bytecode == null) {
            return RiskyChange.NONE;
        }

        if (!this.bytecode.getSuperName().equals(bytecode.getSuperName())) {
            Log.v(
                    "live.deploy",
                    String.format(
                            "Super of %s has changed; proxy objects may need to be recreated.\n\t%s -> %s",
                            this.bytecode.getInternalName(),
                            this.bytecode.getSuperName(),
                            bytecode.getSuperName()));
            return RiskyChange.SUPER_CHANGE;
        }

        if (!Arrays.equals(this.bytecode.getInterfaces(), bytecode.getInterfaces())) {
            Log.v(
                    "live.deploy",
                    String.format(
                            "Interfaces of %s have changed; proxy objects may need to be recreated.\n\told: %s\n\tnew: %s",
                            this.bytecode.getInternalName(),
                            Arrays.stream(this.bytecode.getInterfaces())
                                    .sorted()
                                    .collect(Collectors.joining(", ")),
                            Arrays.stream(bytecode.getInterfaces())
                                    .sorted()
                                    .collect(Collectors.joining(", "))));
            return RiskyChange.INTERFACE_CHANGE;
        }

        if (!this.bytecode.getFieldNames().equals(bytecode.getFieldNames())) {
            Log.v(
                    "live.deploy",
                    String.format(
                            "Fields of %s have changed; proxy objects may need to be recreated.\n\told: %s\n\tnew: %s",
                            this.bytecode.getInternalName(),
                            this.bytecode.getFieldNames().stream()
                                    .sorted()
                                    .collect(Collectors.joining(", ")),
                            bytecode.getFieldNames().stream()
                                    .sorted()
                                    .collect(Collectors.joining(", "))));
            return RiskyChange.FIELD_CHANGE;
        }

        return RiskyChange.NONE;
    }

    private void setUpProxyClass(Interpretable bytecode)
            throws ClassNotFoundException, SecurityException {
        castableTypes.clear();
        superReflectedFields.clear();
        superReflectedMethods.clear();

        HashSet<Class<?>> interfaceClasses = new HashSet<>();
        interfaceClasses.add(ProxyClass.class);

        LinkedList<Class<?>> queue = new LinkedList<>();
        queue.add(classForName(bytecode.getSuperName()));
        for (String proxyInterface : bytecode.getInterfaces()) {
            queue.add(classForName(proxyInterface));
        }

        // Make sure to add the proxied class itself as a castable type.
        castableTypes.add(Type.getObjectType(bytecode.getInternalName()));

        // Traverse the inheritance hierarchy of the class, keeping track of interfaces, methods,
        // and fields.
        while (queue.size() > 0) {
            Class<?> clz = queue.remove();

            if (clz.isInterface()) {
                interfaceClasses.add(clz);
            }

            for (Field field : clz.getDeclaredFields()) {
                field.setAccessible(true);
                superReflectedFields.put(field.getName(), field);
            }

            for (Method method : clz.getDeclaredMethods()) {
                method.setAccessible(true);
                String key = method.getName() + Type.getMethodDescriptor(method);
                superReflectedMethods.put(key, method);
            }

            castableTypes.add(Type.getType(clz));

            Class<?> superclass = clz.getSuperclass();
            if (superclass != null) {
                queue.add(superclass);
            }
            Class<?>[] interfaces = clz.getInterfaces();
            for (Class<?> inter : interfaces) {
                if (!interfaceClasses.contains(inter)) {
                    queue.add(inter);
                }
            }
        }
        proxyClass =
                Proxy.getProxyClass(
                        context.getClassLoader(),
                        interfaceClasses.stream().toArray(Class<?>[]::new));
    }

    private Class<?> classForName(String internalName) throws ClassNotFoundException {
        return Class.forName(internalName.replace('/', '.'), true, context.getClassLoader());
    }

    public String getClassInternalName() {
        return bytecode.getInternalName();
    }
}

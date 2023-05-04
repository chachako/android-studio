/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.testutils;

import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class JarTestSuiteRunner extends Suite {

    /** Putatively temporary mechanism to avoid running certain classes. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ExcludeClasses {

        Class<?>[] value();
    }

    public JarTestSuiteRunner(Class<?> suiteClass, RunnerBuilder builder)
            throws InitializationError, ClassNotFoundException, IOException {
        super(new DelegatingRunnerBuilder(builder), suiteClass, getTestClasses(suiteClass));
    }

    private static Class<?>[] getTestClasses(Class<?> suiteClass)
            throws ClassNotFoundException, IOException {
        String jarSuffix = System.getProperty("test.suite.jar");
        if (jarSuffix == null) {
            throw new RuntimeException(
                    "Must set test.suite.jar to the name of the jar containing JUnit tests");
        }

        long start = System.currentTimeMillis();
        TestGroup testGroup = TestGroup.builder()
                .setClassLoader(suiteClass.getClassLoader())
                .includeJUnit3()
                .excludeClassNames(classNamesToExclude(suiteClass))
                .build();
        List<Class<?>> testClasses = testGroup.scanTestClasses(jarSuffix);
        System.out.printf("Found %d tests in %dms%n", testClasses.size(), (System.currentTimeMillis() - start));
        if (testClasses.isEmpty()) {
            throw new RuntimeException("No tests found in class path using suffix: " + jarSuffix);
        }
        String filter = System.getProperty("test_filter");
        String filterExcludes = System.getProperty("test_exclude_filter");
        Stream<Class<?>> stream = testClasses.stream();
        if (filter != null && !filter.isEmpty()) {
            Predicate<String> filterPredicate = Pattern.compile(filter).asPredicate();
            stream = stream.filter(c -> filterPredicate.test(c.getName()));
        }
        if (filterExcludes != null && !filterExcludes.isEmpty()) {
            Predicate<String> excludePredicate = Pattern.compile(filterExcludes).asPredicate();
            stream = stream.filter(c -> !excludePredicate.test(c.getName()));
        }
        Class<?>[] classes = stream.toArray(i -> new Class<?>[i]);
        System.out.printf("Filtered to %d tests%n", classes.length);
        return classes;
    }


    /** Putatively temporary mechanism to avoid running certain classes. */
    private static Set<String> classNamesToExclude(
            Class<?> suiteClass) {
        Set<String> excludeClassNames = new HashSet<>();
        ExcludeClasses annotation = suiteClass.getAnnotation(ExcludeClasses.class);
        if (annotation != null) {
            for (Class<?> classToExclude : annotation.value()) {
                String className = classToExclude.getCanonicalName();
                if (!excludeClassNames.add(className)) {
                    throw new RuntimeException(
                            String.format(
                                    "on %s, %s value duplicated: %s",
                                    suiteClass.getSimpleName(),
                                    ExcludeClasses.class.getSimpleName(),
                                    className));
                }
            }
        }
        return excludeClassNames;
    }
}

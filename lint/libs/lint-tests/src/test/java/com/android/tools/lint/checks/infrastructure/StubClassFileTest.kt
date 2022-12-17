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

import com.android.SdkConstants.DOT_CLASS
import com.android.tools.lint.checks.AbstractCheckTest.SUPPORT_ANNOTATIONS_JAR
import com.android.tools.lint.checks.infrastructure.TestFiles.binaryStub
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.intellij.lang.jvm.annotation.JvmAnnotationArrayValue
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute
import com.intellij.lang.jvm.annotation.JvmAnnotationAttributeValue
import com.intellij.lang.jvm.annotation.JvmAnnotationClassValue
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import com.intellij.lang.jvm.annotation.JvmNestedAnnotationValue
import com.intellij.openapi.util.Disposer
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.jar.JarFile

/**
 * Unit test for [StubClassFile]; this test will take Java stub files,
 * convert them to class files, and then load these using PSI and pretty
 * print the class file APIs back to something resembling metalava
 * API signatures, checking that in the bytecode we found everything
 * we expect -- methods, parameters, types, generics, throws lists,
 * constant initial values, etc.
 */
@Suppress("LintDocExample")
class StubClassFileTest {
    @get:Rule
    var temporaryFolder = TemporaryFolder()

    private fun check(expected: String, testFile: TestFile, allowKotlin: Boolean = false) {
        val root = temporaryFolder.root
        val classes = mutableSetOf<String>()

        // We need at least one source file such that we can get a UAST context back where the libraries are attached
        val projects = lint().allowKotlinClassStubs(allowKotlin)
            .files(testFile, java("class LintTestPlaceHolder {}"), SUPPORT_ANNOTATIONS_JAR).createProjects(root)

        assertEquals(1, projects.size)
        val jar = File(projects[0], testFile.targetRelativePath)

        JarFile(jar).use { jarFile ->
            jarFile.entries().toList().forEach { entry ->
                val name = entry.name
                if (name.endsWith(DOT_CLASS) && !name.contains("$")) {
                    classes.add(name.removeSuffix(DOT_CLASS).replace('/', '.'))
                }
            }
        }

        val parsed = parse(projects.first())
        val contexts = parsed.first
        val evaluator = contexts.first().evaluator
        val sb = StringBuilder()
        for (className in classes) {
            val cls = evaluator.findClass(className)
            val signatures = cls?.prettyPrint()
            sb.append(signatures)
        }

        Disposer.dispose(parsed.second)

        assertEquals(expected.trimIndent(), sb.toString().trim())
    }

    private fun PsiClass.prettyPrint(): String {
        val sb = StringBuilder()
        accept(object : JavaRecursiveElementVisitor() {
            private var depth = 0
            private fun indent(level: Int) {
                for (i in 0 until level) {
                    sb.append("    ")
                }
            }

            private fun appendModifiers(owner: PsiModifierListOwner) {
                val modifierList = owner.modifierList ?: return
                val annotations = modifierList.annotations
                for (annotation in annotations) {
                    sb.append('@').append(annotation.qualifiedName)
                    val attributes = annotation.attributes
                    if (attributes.size > 0) {
                        sb.append('(')
                        sb.append(
                            attributes.joinToString(", ") { it.toSource() }
                        )
                        sb.append(')')
                    }
                    sb.append(' ')
                }

                if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
                    sb.append("public ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
                    sb.append("protected ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
                    sb.append("private ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.STATIC) &&
                    // enums and interfaces are implicitly static
                    !(owner is PsiClass && (owner.isEnum || owner.isInterface))
                ) {
                    sb.append("static ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.ABSTRACT) &&
                    // interfaces are implicitly abstract and static
                    !(owner is PsiClass && owner.isInterface)
                ) {
                    sb.append("abstract ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.FINAL) &&
                    // enum constants are implicitly final
                    !(owner is PsiClass && owner.isEnum)
                ) {
                    sb.append("final ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.NATIVE)) {
                    sb.append("native ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                    sb.append("synchronized ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.STRICTFP)) {
                    sb.append("strictfp ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.TRANSIENT)) {
                    sb.append("transient ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.VOLATILE)) {
                    sb.append("volatile ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.DEFAULT)) {
                    sb.append("default ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.OPEN)) {
                    sb.append("open ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.TRANSITIVE)) {
                    sb.append("transitive ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.SEALED)) {
                    sb.append("sealed ")
                }
                if (modifierList.hasModifierProperty(PsiModifier.NON_SEALED)) {
                    sb.append("non-sealed ")
                }
            }

            private fun appendType(type: PsiType?) {
                type ?: return
                sb.append(type.canonicalText.replace('$', '.'))
            }

            private fun PsiLiteral.toSource(): String {
                return when (val value = this.value) {
                    is String ->
                        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                    is Char -> "'$value'"
                    else -> value.toString()
                }
            }

            private fun JvmAnnotationConstantValue.toSource(): String {
                return when (val value = this.constantValue) {
                    is String ->
                        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                    is Char -> "'$value'"
                    else -> value.toString()
                }
            }

            private fun JvmAnnotationAttribute.toSource(): String {
                return attributeName + "=" + (attributeValue?.toSource())
            }

            @Suppress("UnstableApiUsage")
            private fun JvmAnnotationAttributeValue.toSource(): String {
                return when (this) {
                    is PsiLiteral -> (this as PsiLiteral).toSource()
                    is JvmAnnotationConstantValue -> this.toSource()
                    is JvmAnnotationClassValue -> "$qualifiedName.class"
                    is JvmNestedAnnotationValue -> "@${value.qualifiedName}(${value.attributes.joinToString { it.toSource() }})"
                    is JvmAnnotationEnumFieldValue -> "$containingClassName.${this.fieldName}"
                    is JvmAnnotationArrayValue -> "{" + values.joinToString(", ") { it.toSource() } + "}"
                    else -> this.toString()
                }
            }

            private fun PsiClass.isAnnotation(): Boolean {
                return isAnnotationType || isInterface &&
                    // bytecode doesn't show up as annotation
                    this.superClass?.qualifiedName == "java.lang.annotation.Annotation"
            }

            override fun visitClass(aClass: PsiClass) {
                indent(depth)
                appendModifiers(aClass)
                if (aClass.isAnnotation()) {
                    sb.append("@interface")
                } else if (aClass.isInterface) {
                    sb.append("interface")
                } else if (aClass.isEnum) {
                    sb.append("enum")
                } else {
                    sb.append("class")
                }
                sb.append(" ${aClass.qualifiedName}")
                val superClass = aClass.superClass?.qualifiedName
                if (aClass.typeParameters.isNotEmpty()) {
                    appendTypeParameters(aClass.typeParameters)
                }
                if (superClass != null && !(aClass.isInterface || aClass.isEnum || aClass.isAnnotation())) {
                    sb.append(" extends ")
                    sb.append(aClass.superTypes.joinToString(",") { it.canonicalText.replace("$", ".") })
                }
                val interfaces = aClass.implementsListTypes
                if (interfaces.isNotEmpty()) {
                    sb.append(" implements ")
                    sb.append(interfaces.joinToString(",") { it.canonicalText.replace("$", ".") })
                }
                sb.append(" {\n")
                depth++

                if (aClass.isEnum) {
                    val enumFields = aClass.fields.filter { isEnumConstant(it) }
                    if (enumFields.isNotEmpty()) {
                        indent(depth)
                        sb.append(enumFields.joinToString(", ") { it.name }).append(";\n")
                    }
                }

                super.visitClass(aClass)
                depth--
                indent(depth)
                sb.append("}\n")
            }

            private fun appendTypeParameters(typeParameters: Array<PsiTypeParameter>) {
                sb.append('<')
                typeParameters.forEachIndexed { index, parameter ->
                    if (index > 0) sb.append(", ")
                    sb.append(parameter.name)
                    val qualifiedName = parameter.superClass?.qualifiedName
                    if (qualifiedName != null && qualifiedName != "java.lang.Object") {
                        sb.append(" extends ").append(qualifiedName)
                    }
                    sb.append(parameter.interfaces.joinToString("") { itf -> " & ${itf.qualifiedName}" })
                }
                sb.append('>')
            }

            override fun visitMethod(method: PsiMethod) {
                if (method.isConstructor && (
                    method.containingClass?.isEnum == true ||
                        method.containingClass?.isAnnotation() == true
                    )
                ) {
                    return
                }

                indent(depth)
                appendModifiers(method)
                if (!method.isConstructor) {
                    appendType(method.returnType)
                    sb.append(' ')
                }

                val typeParameters = method.typeParameters
                if (typeParameters.isNotEmpty()) {
                    appendTypeParameters(typeParameters)
                    sb.append(' ')
                }

                sb.append("${method.name}(")
                method.parameterList.parameters.forEachIndexed { i, parameter ->
                    if (i > 0) {
                        sb.append(", ")
                    }
                    appendModifiers(parameter)
                    sb.append(parameter.type.canonicalText)
                    val name = parameter.name
                    sb.append(" $name")
                }
                sb.append(")")
                val throws = method.throwsList.referencedTypes
                if (throws.isNotEmpty()) {
                    sb.append(" throws ")
                    sb.append(throws.joinToString(", ") { it.className })
                }

                sb.append(";\n")

                super.visitMethod(method)
            }

            private fun isEnumConstant(field: PsiField): Boolean {
                // For source, we can just look to see if it's PsiEnumConstant,
                // but from class files they're "just" fields
                return field.containingClass?.isEnum == true &&
                    (field.type as? PsiClassType)?.canonicalText == field.containingClass?.qualifiedName
            }

            override fun visitField(field: PsiField) {
                if (!isEnumConstant(field)) { // Handled inline in the class visitor
                    indent(depth)
                    appendModifiers(field)
                    appendType(field.type)
                    sb.append(" ${field.name}")
                    val initial = field.computeConstantValue()
                    if (initial != null) {
                        sb.append(" = $initial")
                    }
                    sb.append(";\n")
                }
                super.visitField(field)
            }
        })
        return sb.toString()
    }

    @Test
    fun testBasic() {
        check(
            """
            public class com.example.myapplication.Test extends java.lang.Object {
                public static final java.lang.String MY_CONSTANT = myconst;
                public static final int MY_FORTY_TWO = 42;
                public static final int MY_FORTY_THREE = 43;
                public final int MY_FORTY_FOUR = 44;
                public static final int[] MY_ARRAY;
                public Test();
                public class com.example.myapplication.Test.InnerClass extends java.lang.Object {
                    public InnerClass();
                    public void test(int first, float second, java.lang.String third, boolean[] fourth) throws IOException;
                }
                public class com.example.myapplication.Test.Other extends com.example.myapplication.Test.InnerClass,java.lang.Runnable implements java.lang.Runnable {
                    Other(float test);
                    public void run();
                    private void privateMethod();
                }
                interface com.example.myapplication.Test.MyInterface {
                    public abstract void required();
                }
                enum com.example.myapplication.Test.MyEnum {
                    FOO, BAR;
                    private final int myField = 0;
                    public static final java.lang.String OTHER = other;
                    public java.lang.String displayName();
                }
            }
            """.trimIndent(),

            binaryStub(
                "libs/test.jar",
                java(
                    """
                    package com.example.myapplication;

                    import java.io.IOException;
                    import java.util.List;
                    import java.util.RandomAccess;

                    public class Test {
                        public static final String MY_CONSTANT = "myconst";
                        public static final int MY_FORTY_TWO = 42;
                        public static final int MY_FORTY_THREE = 21 * 2 + 1;
                        public final int MY_FORTY_FOUR = 44;
                        public static final int[] MY_ARRAY = new int[] { 1, 2, 3};
                        public class InnerClass {
                            @SuppressWarnings("RedundantThrows")
                            public void test(int first, float second, String third, boolean[] fourth) throws IOException {
                            }
                        }
                        public class Other extends InnerClass implements Runnable {
                            // Note that the signature here will include the outer type too because it's an instance class!
                            // (Lcom/example/myapplication/Test;F)V
                            Other(float test) { }
                            @Override public void run() { }
                            private void privateMethod() { }
                        }
                        interface MyInterface {
                            void required();
                        }
                        enum MyEnum {
                            FOO, BAR;
                            public String displayName() { return null; }
                            private final int myField = 0;
                            public static final String OTHER = "other";
                        }
                    }
                    """
                ).indented()
            )
        )
    }

    @Test
    fun testGenerics() {
        check(
            """
            public class test.pkg.DiffUtil extends java.lang.Object {
                public DiffUtil();
                public static abstract class test.pkg.DiffUtil.ItemCallback<S, T extends java.util.List & java.util.List & java.util.RandomAccess, FOO> extends test.pkg.DiffUtil.Foo<FOO,T> {
                    private java.util.List<? extends java.lang.Number> field1;
                    private S field2;
                    public ItemCallback();
                    public abstract boolean areItemsTheSame(T oldItem, FOO newItem);
                    public boolean areContentsTheSame(T oldItem, T newItem);
                    public void test(java.util.List<? super java.lang.Integer> s);
                    public static int <F extends java.lang.Number, G extends java.lang.Long> print(F num, G num);
                    public static float <F extends java.lang.Double> print(F num);
                }
                class test.pkg.DiffUtil.ExceptionThrower<T extends java.io.IOException> extends java.lang.Object {
                    public ExceptionThrower();
                    void test() throws T;
                }
                public static class test.pkg.DiffUtil.Foo<A, B> extends java.lang.Object {
                    public Foo();
                }
                public static class test.pkg.DiffUtil.Bar<FOO extends java.lang.Number & java.util.RandomAccess> extends test.pkg.DiffUtil.Foo<java.lang.String,java.lang.String> {
                    public Bar();
                }
            }
            """.trimIndent(),

            binaryStub(
                "libs/diffutil.jar",
                stubSources = listOf(
                    java(
                        """
                        package test.pkg;

                        import java.io.IOException;
                        import java.util.List;
                        import java.util.RandomAccess;

                        public class DiffUtil {
                            // <S:Ljava/lang/Object;T::Ljava/util/List<-Ljava/lang/Integer;>;:Ljava/util/RandomAccess;FOO:Ljava/lang/Object;>Ltest/pkg/DiffUtil＄Foo<TFOO;TT;>;
                            public abstract static class ItemCallback<S, T extends List<? super Integer> & RandomAccess, FOO> extends Foo<FOO, T> {
                                //  (TT;TFOO;)Z
                                public abstract boolean areItemsTheSame(T oldItem, FOO newItem);

                                //  (TT;TT;)Z
                                public  boolean areContentsTheSame(T oldItem, T newItem) {
                                    throw new UnsupportedOperationException();
                                }

                                //  (Ljava/util/List<-Ljava/lang/Integer;>;)V
                                public void test(List<? super Integer> s) {
                                }

                                //  <F:Ljava/lang/Number;>(TF;)I
                                public static <F extends Number, G extends Long> int print(F num, G num) {
                                    return 0;
                                }
                                public static <F extends Double> float print(F num) {
                                    return 0;
                                }

                                // Ljava/util/List<+Ljava/lang/Number;>;
                                private List<? extends Number> field1;

                                // TS;
                                private S field2;
                            }

                            // <T:Ljava/io/IOException;>Ljava/lang/Object;
                            class ExceptionThrower<T extends IOException> {
                                // ()V^TT
                                @SuppressWarnings("RedundantThrows")
                                void test() throws T {
                                }
                            }

                            //  <A:Ljava/lang/Object;B:Ljava/lang/Object;>Ljava/lang/Object;
                            public static class Foo<A, B> {
                            }

                            // <FOO:Ljava/lang/Number;:Ljava/util/RandomAccess;>Ltest/pkg/DiffUtil＄Foo<Ljava/lang/String;Ljava/lang/String;>;
                            public static class Bar<FOO extends Number & RandomAccess> extends Foo<String, String> {
                            }
                        }
                        """
                    ).indented()
                )
            )
        )
    }

    @Test
    fun testAnnotations() {
        check(
            """
            public class test.pkg.TestAnnotations extends java.lang.Object {
                public static final int MY_CONSTANT = 15;
                public TestAnnotations();
                @androidx.annotation.MainThread public void testNoArgs(@androidx.annotation.StringRes int res);
                public void testInt(@androidx.annotation.Size(min=5) java.lang.String arg);
                public void testIntConstant(@androidx.annotation.Size(min=15) java.lang.String arg);
                @androidx.annotation.RestrictTo(value={androidx.annotation.RestrictTo.Scope.TESTS}) public void testEnums();
                @androidx.annotation.RestrictTo(value={androidx.annotation.RestrictTo.Scope.GROUP_ID, androidx.annotation.RestrictTo.Scope.TESTS}) public void testEnumsArray();
                @androidx.annotation.RequiresPermission(value="android.permission.ACCESS_FINE_LOCATION") public void testString();
                @androidx.annotation.RequiresPermission(anyOf={"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION"}) public void testStringArray();
                @androidx.annotation.RequiresPermission.Read(value=@androidx.annotation.RequiresPermission()) public void testNestedPermission();
                @androidx.annotation.RequiresPermission.Read(value=@androidx.annotation.RequiresPermission(value="android.permission.ACCESS_FINE_LOCATION")) public void testNestedPermission2();
                @test.pkg.TestAnnotations.BooleanAnnotation(bool=true, booleans={true, false}) public void testBoolean();
                @test.pkg.TestAnnotations.DoubleAnnotation(dbl=2.0, doubles={1.0, 2.5}) public void testDoubles();
                @test.pkg.TestAnnotations.LongAnnotation(l=2, longs={1, 10}) public void testLongs();
                @test.pkg.TestAnnotations.ClassAnnotation(cls=java.lang.Float.class) public void testClass();
                @test.pkg.TestAnnotations.ClassAnnotation(classes={java.lang.Float.class, java.lang.Integer.class}) public void testClasses();
                @interface test.pkg.TestAnnotations.BooleanAnnotation {
                    public abstract boolean bool();
                    public abstract boolean[] booleans();
                }
                @interface test.pkg.TestAnnotations.DoubleAnnotation {
                    public abstract double dbl();
                    public abstract double[] doubles();
                }
                @interface test.pkg.TestAnnotations.LongAnnotation {
                    public abstract long l();
                    public abstract long[] longs();
                }
                @interface test.pkg.TestAnnotations.ClassAnnotation {
                    public abstract java.lang.Class<? extends java.lang.Number> cls();
                    public abstract java.lang.Class<? extends java.lang.Number>[] classes();
                }
                @interface test.pkg.TestAnnotations.MyAnno {
                    public abstract int[] values();
                    public abstract java.lang.String foo();
                    public abstract float max();
                    public abstract int value();
                    public abstract int foobar();
                }
                @test.pkg.TestAnnotations.MyAnno(value=3, foobar=5, max=0.5, foo="Test", values={1, 2, 3}) public static class test.pkg.TestAnnotations.TestAnnotationsOnClass extends java.lang.Object {
                    public TestAnnotationsOnClass();
                }
                public class test.pkg.TestAnnotations.TestAnnotationsOnParameter extends java.lang.Object {
                    TestAnnotationsOnParameter(@test.pkg.TestAnnotations.MyAnno(value=4) float f);
                }
            }
            """.trimIndent(),
            binaryStub(
                "libs/annotations.jar",
                stubSources = listOf(
                    java(
                        """
                        package test.pkg;

                        import android.Manifest;
                        import androidx.annotation.*;

                        @SuppressWarnings({"RedundantSuppression", "DefaultAnnotationParam"})
                        public class TestAnnotations {
                            public static final int MY_CONSTANT = 15;

                            @MainThread
                            public void testNoArgs(@StringRes int res) {
                            }

                            public void testInt(@Size(min = 5) String arg) {
                            }

                            public void testIntConstant(@Size(min = MY_CONSTANT) String arg) {
                            }

                            @RestrictTo(RestrictTo.Scope.TESTS)
                            public void testEnums() {
                            }

                            @RestrictTo({RestrictTo.Scope.GROUP_ID, RestrictTo.Scope.TESTS})
                            public void testEnumsArray() {
                            }

                            @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            public void testString() {
                            }

                            @RequiresPermission(anyOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION})
                            public void testStringArray() {
                            }

                            @RequiresPermission.Read(@RequiresPermission)
                            public void testNestedPermission() { }

                            @RequiresPermission.Read(@RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION))
                            public void testNestedPermission2() { }

                            @BooleanAnnotation(bool = true, booleans = {true, false})
                            public void testBoolean() {
                            }

                            @interface BooleanAnnotation {
                                boolean bool() default false;

                                boolean[] booleans() default {};
                            }

                            @DoubleAnnotation(dbl = 2.0, doubles = {1.0, 2.5})
                            public void testDoubles() {
                            }

                            @interface DoubleAnnotation {
                                double dbl() default 1.0;

                                double[] doubles() default {};
                            }

                            @LongAnnotation(l = 2L, longs = {1, 10})
                            public void testLongs() {
                            }

                            @interface LongAnnotation {
                                long l() default 1;

                                long[] longs() default {};
                            }

                            @interface ClassAnnotation {
                                Class<? extends Number> cls() default Integer.class;

                                Class<? extends Number>[] classes() default Integer.class;
                            }

                            @ClassAnnotation(cls = Float.class)
                            public void testClass() {
                            }

                            @ClassAnnotation(classes = {Float.class, Integer.class})
                            public void testClasses() {
                            }

                            @interface MyAnno {
                                int[] values() default {};
                                String foo() default "";
                                float max() default 1.0f;
                                int value();
                                int foobar() default 5;
                            }


                            @MyAnno(value = 3, foobar = 5, max = 0.5f, foo = "Test", values = {1, 2, 3})
                            public static class TestAnnotationsOnClass {
                            }

                            public class TestAnnotationsOnParameter {
                                TestAnnotationsOnParameter(@MyAnno(4) float f) {
                                    super();
                                }
                            }
                        }
                        """
                    ).indented()
                ),
                compileOnly = listOf(
                    SUPPORT_ANNOTATIONS_JAR,
                    java(
                        """
                        package android;
                        public class Manifest {
                            public static final class permission {
                                public static final String ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION";
                                public static final String ACCESS_BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION";
                            }
                        }
                        """
                    ).indented()
                )
            )
        )
    }

    @Test
    fun testKotlin() {
        check(
            """
            public final class test.pkg.MyAnnotationKt extends java.lang.Object {
                public MyAnnotationKt();
                public static final void topLevelFunction(@test.pkg.MyAnnotation int p);
            }
            @java.lang.annotation.Retention(value=java.lang.annotation.RetentionPolicy.RUNTIME) public @interface test.pkg.MyAnnotation {
            }
            public final class test.pkg.MyClass extends java.lang.Object {
                @org.jetbrains.annotations.NotNull public static final test.pkg.MyClass.Companion Companion;
                public static final int MY_CONSTANT = 1;
                @org.jetbrains.annotations.NotNull private final java.lang.String myProperty;
                @org.jetbrains.annotations.NotNull public final java.lang.String getMyProperty();
                public MyClass(@org.jetbrains.annotations.NotNull java.lang.String myProperty);
                public MyClass(boolean b);
                @kotlin.jvm.JvmStatic public static final void myCompanionMethod();
                public static final class test.pkg.MyClass.Companion extends java.lang.Object {
                    @kotlin.jvm.JvmStatic public final void myCompanionMethod();
                    private Companion();
                }
            }
            """.trimIndent(),

            binaryStub(
                "libs/test.jar",
                kotlin(
                    """
                    package test.pkg

                    annotation class MyAnnotation
                    fun topLevelFunction(@MyAnnotation int: Int) { }
                    class MyClass(val myProperty: String) {
                        constructor(b: Boolean) : this("true")
                        companion object {
                            const val MY_CONSTANT = 1
                            @JvmStatic fun myCompanionMethod() {}
                        }
                    }
                    """
                ).indented()
            ),
            allowKotlin = true
        )
    }

    @Test
    fun testPackageInfo() {
        check(
            """
            @androidx.annotation.RestrictTo(value={androidx.annotation.RestrictTo.Scope.GROUP_ID}) interface library.pkg.internal.package-info {
            }
            """.trimIndent(),

            binaryStub(
                "libs/test.jar",
                stubSources = listOf(
                    java(
                        """
                        @RestrictTo(RestrictTo.Scope.GROUP_ID)
                        package library.pkg.internal;

                        import androidx.annotation.RestrictTo;
                        """
                    ).indented()
                ),
                compileOnly = listOf(SUPPORT_ANNOTATIONS_JAR)
            )
        )
    }

    @Test
    fun testNotStubbable() {
        try {
            check(
                "",
                binaryStub(
                    "libs/test.jar",
                    java(
                        """
                        package test.pkg;
                        public class Test {
                            public String getName() { return ""; }
                            public String displayName() { return getName(); }
                        }
                        """
                    ).indented()
                )
            )
            fail("Unstubbable method `displayName` should have raised an error")
        } catch (e: Throwable) {
            assertEquals(
                """
                Method `test.pkg.Test.displayName()Ljava/lang/String;` is not just a stub method;
                it contains code, which the testing infrastructure bytecode stubber can't handle.
                You'll need to switch to a `compiled` or `bytecode` test file type instead (where
                you precompile the source code using a compiler), *or*, if the method body etc isn't
                necessary for the test, remove it and replace with a simple return or throw.
                Method body: { return getName(); }

                """.trimIndent(),
                e.message
            )
        }
    }

    @Test
    fun testMustOptInToKotlin() {
        try {
            check(
                "",
                binaryStub(
                    "libs/test.jar",
                    kotlin(
                        """
                        package test.pkg
                        class Test
                    """
                    ).indented()
                )
            )
            fail("Using Kotlin without opting in should throw exception")
        } catch (e: Throwable) {
            assertEquals(
                """
                You cannot use Kotlin in a binaryStub or mavenLibrary unless you also turn on
                `lint().allowKotlinClassStubs(true)`. Kotlin stubs work in general, but module
                metadata is still missing, which means that if your test relies on this metadata
                (for example to call package level functions from Kotlin, or to access things like
                default values or inline methods), that will not work.
                """.trimIndent(),
                e.message
            )
        }
    }
}

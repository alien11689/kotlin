/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.types;

import com.google.common.collect.Maps;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.descriptors.ClassDescriptor;
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor;
import org.jetbrains.kotlin.descriptors.ModuleDescriptor;
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor;
import org.jetbrains.kotlin.diagnostics.Diagnostic;
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages;
import org.jetbrains.kotlin.incremental.components.NoLookupLocation;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.psi.JetFile;
import org.jetbrains.kotlin.psi.JetPsiFactoryKt;
import org.jetbrains.kotlin.psi.JetTypeReference;
import org.jetbrains.kotlin.renderer.DescriptorRenderer;
import org.jetbrains.kotlin.resolve.AnalyzingUtils;
import org.jetbrains.kotlin.resolve.BindingTrace;
import org.jetbrains.kotlin.resolve.BindingTraceContext;
import org.jetbrains.kotlin.resolve.lazy.KotlinTestWithEnvironment;
import org.jetbrains.kotlin.resolve.lazy.LazyResolveTestUtil;
import org.jetbrains.kotlin.resolve.scopes.*;
import org.jetbrains.kotlin.resolve.scopes.utils.ScopeUtilsKt;
import org.jetbrains.kotlin.test.ConfigurationKind;
import org.jetbrains.kotlin.test.JetTestUtils;
import org.jetbrains.kotlin.tests.di.ContainerForTests;
import org.jetbrains.kotlin.tests.di.InjectionKt;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.jetbrains.kotlin.psi.PsiPackage.JetPsiFactory;

@SuppressWarnings("unchecked")
public class TypeSubstitutorTest extends KotlinTestWithEnvironment {
    private JetScope scope;
    private ContainerForTests container;

    @Override
    protected KotlinCoreEnvironment createEnvironment() {
        return createEnvironmentWithMockJdk(ConfigurationKind.JDK_ONLY);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        container = InjectionKt.createContainerForTests(getProject(), JetTestUtils.createEmptyModule());
        scope = getContextScope();
    }

    @Override
    protected void tearDown() throws Exception {
        container = null;
        scope = null;
        super.tearDown();
    }

    private JetScope getContextScope() throws IOException {
        // todo comments
        String text = FileUtil.loadFile(new File("compiler/testData/type-substitutor.kt"), true);
        JetFile jetFile = JetPsiFactoryKt.JetPsiFactory(getProject()).createFile(text);
        ModuleDescriptor module = LazyResolveTestUtil.resolveLazily(Collections.singletonList(jetFile), getEnvironment());
        JetScope topLevelDeclarations = module.getPackage(FqName.ROOT).getMemberScope();
        ClassifierDescriptor contextClass = topLevelDeclarations.getClassifier(Name.identifier("___Context"), NoLookupLocation.FROM_TEST);
        assert contextClass instanceof ClassDescriptor;
        WritableScopeImpl typeParameters = new WritableScopeImpl(JetScope.Empty.INSTANCE$, module, RedeclarationHandler.THROW_EXCEPTION,
                                      "Type parameter scope");
        for (TypeParameterDescriptor parameterDescriptor : contextClass.getTypeConstructor().getParameters()) {
            typeParameters.addClassifierDescriptor(parameterDescriptor);
        }
        typeParameters.changeLockLevel(WritableScope.LockLevel.READING);
        return new ChainedScope(module,
                                "TypeSubstitutorTest::getContextScope()",
                                topLevelDeclarations,
                                typeParameters,
                                contextClass.getDefaultType().getMemberScope(),
                                module.getBuiltIns().getBuiltInsPackageScope());
    }

    private void doTest(@Nullable String expectedTypeStr, String initialTypeStr, Pair<String, String>... substitutionStrs) {
        JetType initialType = resolveType(initialTypeStr);

        Map<TypeConstructor, TypeProjection> map = stringsToSubstitutionMap(substitutionStrs);
        TypeSubstitutor substitutor = TypeSubstitutor.create(map);

        JetType result = substitutor.substitute(initialType, Variance.INVARIANT);

        if (expectedTypeStr == null) {
            assertNull(result);
        }
        else {
            assertNotNull(result);
            assertEquals(expectedTypeStr, DescriptorRenderer.SHORT_NAMES_IN_TYPES.renderType(result));
        }
    }

    private Map<TypeConstructor, TypeProjection> stringsToSubstitutionMap(Pair<String, String>[] substitutionStrs) {
        Map<TypeConstructor, TypeProjection> map = Maps.newHashMap();
        for (Pair<String, String> pair : substitutionStrs) {
            String typeParameterName = pair.first;
            String replacementProjectionString = pair.second;

            ClassifierDescriptor classifier = scope.getClassifier(Name.identifier(typeParameterName), NoLookupLocation.FROM_TEST);
            assertNotNull("No type parameter named " + typeParameterName, classifier);
            assertTrue(typeParameterName + " is not a type parameter: " + classifier, classifier instanceof TypeParameterDescriptor);

            String typeStr = "C<" + replacementProjectionString + ">";
            JetType typeWithArgument = resolveType(typeStr);
            assert !typeWithArgument.getArguments().isEmpty() : "No arguments: " + typeWithArgument + " from " + typeStr;

            map.put(classifier.getTypeConstructor(), typeWithArgument.getArguments().get(0));
        }
        return map;
    }

    private JetType resolveType(String typeStr) {
        JetTypeReference jetTypeReference = JetPsiFactoryKt.JetPsiFactory(getProject()).createType(typeStr);
        AnalyzingUtils.checkForSyntacticErrors(jetTypeReference);
        BindingTrace trace = new BindingTraceContext();
        JetType type = container.getTypeResolver().resolveType(ScopeUtilsKt.asLexicalScope(scope), jetTypeReference, trace, true);
        if (!trace.getBindingContext().getDiagnostics().isEmpty()) {
            fail("Errors:\n" + StringUtil.join(
                    trace.getBindingContext().getDiagnostics(),
                    new Function<Diagnostic, String>() {
                        @Override
                        public String fun(Diagnostic diagnostic) {
                            return DefaultErrorMessages.render(diagnostic);
                        }
                    },
                    "\n"));
        }
        return type;
    }

    @NotNull
    private static Pair<String, String> map(String typeParameterName, String replacementProjectionString) {
        return Pair.create(typeParameterName, replacementProjectionString);
    }

    public void testNoOccurrence() throws Exception {
        doTest(
                "C<Int>",
                "C<Int>",
                map("T", "String")
        );
    }

    public void testSimpleOccurrence() throws Exception {
        doTest(
                "C<String>",
                "C<T>",
                map("T", "String")
        );
    }

    public void testSimpleOutProjectionInReplacement() throws Exception {
        doTest(
                "C<out String>",
                "C<T>",
                map("T", "out String")
        );
    }

    public void testSimpleInProjectionInReplacement() throws Exception {
        doTest(
                "C<in String>",
                "C<T>",
                map("T", "in String")
        );
    }

    public void testSimpleOutProjectionInSubject() throws Exception {
        doTest(
                "C<out String>",
                "C<out T>",
                map("T", "String")
        );
    }

    public void testSimpleInProjectionInSubject() throws Exception {
        doTest(
                "C<in String>",
                "C<in T>",
                map("T", "String")
        );
    }

    public void testOutOutProjection() throws Exception {
        doTest(
                "C<out String>",
                "C<out T>",
                map("T", "out String")
        );
    }

    public void testInInProjection() throws Exception {
        doTest(
                "C<in String>",
                "C<in T>",
                map("T", "in String")
        );
    }

    public void testInOutProjection() throws Exception {
        doTest(
                null,
                "C<in T>",
                map("T", "out String")
        );
    }

    public void testOutInProjection() throws Exception {
        doTest(
                "C<out Any?>",
                "C<out T>",
                map("T", "in String")
        );
    }

    public void testOutOutProjectionDeclarationSite() throws Exception {
        doTest(
                "Out<String>",
                "Out<T>",
                map("T", "out String")
        );
    }

    public void testInInProjectionDeclarationSite() throws Exception {
        doTest(
                "In<String>",
                "In<T>",
                map("T", "in String")
        );
    }

    public void testInOutProjectionDeclarationSite() throws Exception {
        doTest(
                "In<*>",
                "In<T>",
                map("T", "out String")
        );
    }

    public void testOutInProjectionDeclarationSite() throws Exception {
        doTest(
                "Out<*>",
                "Out<T>",
                map("T", "in String")
        );
    }

    public void testTwoParameters() throws Exception {
        doTest(
                "P<Int, String>",
                "P<T, R>",
                map("T", "Int"),
                map("R", "String")
        );
    }

    public void testDeepType() throws Exception {
        doTest(
                "C<P<Int, P<Int, String>>>",
                "C<P<T,   P<T,   R>>>",
                map("T", "Int"),
                map("R", "String")
        );
    }

    public void testShallowType() throws Exception {
        doTest(
                "String",
                "T",
                map("T", "String")
        );
    }

    public void testShallowTypeNullable() throws Exception {
        doTest(
                "String?",
                "T?",
                map("T", "String")
        );
    }

    public void testShallowTypeNullableReplacement() throws Exception {
        doTest(
                "String?",
                "T",
                map("T", "String?")
        );
    }

    public void testShallowTypeNullableOnBothEnds() throws Exception {
        doTest(
                "String?",
                "T?",
                map("T", "String?")
        );
    }

    public void testNothingType() throws Exception {
        doTest(
                "Nothing",
                "Nothing",
                map("T", "String")
        );
    }

    public void testCallSiteNullable() throws Exception {
        doTest(
                "C<String?>",
                "C<T?>",
                map("T", "String")
        );
    }

    public void testReplacementNullable() throws Exception {
        doTest(
                "C<String?>",
                "C<T>",
                map("T", "String?")
        );
    }

    public void testCallSiteNullableWithProjection() throws Exception {
        doTest(
                "C<out String?>",
                "C<T?>",
                map("T", "out String")
        );
    }

    public void testReplacementNullableWithProjection() throws Exception {
        doTest(
                "C<out String?>",
                "C<out T>",
                map("T", "String?")
        );
    }

    public void testCallSiteNullableWithConsumedProjection() throws Exception {
        doTest(
                "Out<String?>",
                "Out<T?>",
                map("T", "out String")
        );
    }

    public void testReplacementNullableConsumedProjection() throws Exception {
        doTest(
                "In<String?>",
                "In<T>",
                map("T", "in String?")
        );
    }

    //public void testTwoParametersInChain() throws Exception {
    //    doTest(
    //            "P<Int, Int>",
    //            "P<T, R>",
    //            map("T", "Int"),
    //            map("R", "T")
    //    );
    //}

    public void testStarProjection() throws Exception {
        doTest(
                "Rec<*>",
                "Rec<*>",
                map("T", "String")
        );
    }

    public void testStarProjectionOut() throws Exception {
        doTest(
                "Out<*>",
                "Out<*>",
                map("T", "String")
        );
    }

}

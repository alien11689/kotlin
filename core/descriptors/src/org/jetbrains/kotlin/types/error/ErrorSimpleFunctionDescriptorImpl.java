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

package org.jetbrains.kotlin.types.error;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.*;
import org.jetbrains.kotlin.descriptors.annotations.Annotations;
import org.jetbrains.kotlin.descriptors.impl.FunctionDescriptorImpl;
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.types.ErrorUtils;
import org.jetbrains.kotlin.types.KotlinType;
import org.jetbrains.kotlin.types.TypeSubstitution;

import java.util.Collection;
import java.util.List;

public class ErrorSimpleFunctionDescriptorImpl extends SimpleFunctionDescriptorImpl {
    // used for diagnostic only
    @SuppressWarnings({"UnusedDeclaration", "FieldCanBeLocal"})
    private final ErrorUtils.ErrorScope ownerScope;

    public ErrorSimpleFunctionDescriptorImpl(@NotNull ClassDescriptor containingDeclaration, @NotNull ErrorUtils.ErrorScope ownerScope) {
        super(containingDeclaration, null, Annotations.Companion.getEMPTY(), Name.special("<ERROR FUNCTION>"), Kind.DECLARATION, SourceElement.NO_SOURCE);
        this.ownerScope = ownerScope;
    }

    @NotNull
    @Override
    protected FunctionDescriptorImpl createSubstitutedCopy(
            @NotNull DeclarationDescriptor newOwner,
            @Nullable FunctionDescriptor original,
            @NotNull Kind kind,
            @Nullable Name newName,
            boolean preserveSource
    ) {
        return this;
    }

    @NotNull
    @Override
    public SimpleFunctionDescriptor copy(DeclarationDescriptor newOwner, Modality modality, Visibility visibility, Kind kind, boolean copyOverrides) {
        return this;
    }

    @NotNull
    @Override
    public CopyBuilder<? extends SimpleFunctionDescriptor> newCopyBuilder() {
        return new CopyBuilder<SimpleFunctionDescriptor>() {
            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setOwner(@NotNull DeclarationDescriptor owner) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setModality(@NotNull Modality modality) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setVisibility(@NotNull Visibility visibility) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setKind(@NotNull Kind kind) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setCopyOverrides(boolean copyOverrides) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setName(@NotNull Name name) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setValueParameters(@NotNull List<ValueParameterDescriptor> parameters) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setSubstitution(@NotNull TypeSubstitution substitution) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setTypeParameters(@NotNull List<TypeParameterDescriptor> parameters) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setReturnType(@NotNull KotlinType type) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setExtensionReceiverType(@Nullable KotlinType type) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setDispatchReceiverParameter(@Nullable ReceiverParameterDescriptor dispatchReceiverParameter) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setOriginal(@NotNull FunctionDescriptor original) {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setSignatureChange() {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setPreserveSourceElement() {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setDropOriginalInContainingParts() {
                return this;
            }

            @NotNull
            @Override
            public CopyBuilder<SimpleFunctionDescriptor> setHiddenToOvercomeSignatureClash() {
                return this;
            }

            @Nullable
            @Override
            public SimpleFunctionDescriptor build() {
                return ErrorSimpleFunctionDescriptorImpl.this;
            }
        };
    }

    @Override
    public void setOverriddenDescriptors(@NotNull Collection<? extends CallableMemberDescriptor> overriddenDescriptors) {
        // nop
    }
}

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

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.rename.RenameProcessor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters3
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.types.JetType

class DeprecatedDelegatePropertyConventionFix(
        element: JetNamedFunction,
        private val newName: String
) : JetIntentionAction<JetNamedFunction>(element), CleanupFix {
    override fun getText(): String = "Rename to '$newName'"
    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, file: JetFile) {
        RenameProcessor(project, element, newName, false, false).run()
    }

    companion object : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            @Suppress("UNCHECKED_CAST")
            val params = diagnostic as? DiagnosticWithParameters3<JetExpression, FunctionDescriptor, JetType, String> ?: return null
            val element = DescriptorToSourceUtils.descriptorToDeclaration(params.a) as? JetNamedFunction ?: return null
            return DeprecatedDelegatePropertyConventionFix(element, params.c)
        }
    }
}

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

import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.packageDependencies.DependencyValidationManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.JetBundle
import org.jetbrains.kotlin.idea.actions.KotlinAddImportAction
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.getResolveScope
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import org.jetbrains.kotlin.idea.core.getResolutionScope
import org.jetbrains.kotlin.idea.core.isVisible
import org.jetbrains.kotlin.idea.project.ProjectStructureUtil
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.lexer.JetTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isImportDirectiveExpression
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions
import org.jetbrains.kotlin.utils.CachedValueProperty
import org.jetbrains.kotlin.utils.addToStdlib.singletonList
import org.jetbrains.kotlin.utils.addToStdlib.singletonOrEmptyList
import java.util.*

/**
 * Check possibility and perform fix for unresolved references.
 */
abstract class AutoImportFixBase<T: JetExpression>(expression: T, val diagnostics: Collection<Diagnostic>):
        JetHintAction<T>(expression), HighPriorityAction {

    protected constructor(expression: T, diagnostic: Diagnostic? = null) : this(expression, diagnostic.singletonOrEmptyList())

    private val modificationCountOnCreate = PsiModificationTracker.SERVICE.getInstance(element.getProject()).getModificationCount()

    @Volatile private var anySuggestionFound: Boolean? = null

    public val suggestions: Collection<DeclarationDescriptor> by CachedValueProperty(
            {
                val descriptors = computeSuggestions()
                anySuggestionFound = !descriptors.isEmpty()
                descriptors
            },
            { PsiModificationTracker.SERVICE.getInstance(element.getProject()).getModificationCount() })

    protected abstract fun getSupportedErrors(): Collection<DiagnosticFactory<*>>
    protected abstract fun getCallTypeAndReceiver(): CallTypeAndReceiver<*, *>
    protected abstract fun getImportNames(): Collection<Name>

    override fun showHint(editor: Editor): Boolean {
        if (!element.isValid() || isOutdated()) return false

        if (ApplicationManager.getApplication().isUnitTestMode() && HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) return false

        if (suggestions.isEmpty()) return false

        val addImportAction = createAction(element.project, editor)
        val hintText = ShowAutoImportPass.getMessage(suggestions.size > 1, addImportAction.highestPriorityFqName.asString())
        HintManager.getInstance().showQuestionHint(editor, hintText, element.getTextOffset(), element.getTextRange()!!.getEndOffset(), addImportAction)

        return true
    }

    override fun getText() = JetBundle.message("import.fix")

    override fun getFamilyName() = JetBundle.message("import.fix")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) =
            (super.isAvailable(project, editor, file)) && (anySuggestionFound ?: !suggestions.isEmpty())

    override fun invoke(project: Project, editor: Editor?, file: JetFile) {
        CommandProcessor.getInstance().runUndoTransparentAction {
            createAction(project, editor!!).execute()
        }
    }

    override fun startInWriteAction() = true

    private fun isOutdated() = modificationCountOnCreate != PsiModificationTracker.SERVICE.getInstance(element.getProject()).getModificationCount()

    protected open fun createAction(project: Project, editor: Editor) = KotlinAddImportAction(project, editor, element, suggestions)

    protected fun computeSuggestions(): Collection<DeclarationDescriptor> {
        if (!element.isValid()) return listOf()
        if (element.getContainingFile() !is JetFile) return emptyList()

        val callTypeAndReceiver = getCallTypeAndReceiver()

        if (callTypeAndReceiver is CallTypeAndReceiver.UNKNOWN) return emptyList()

        var referenceNames = getImportNames()
        if (referenceNames.isEmpty()) return emptyList()

        return referenceNames.flatMapTo(LinkedHashSet()) {
            computeSuggestionsForName(it, callTypeAndReceiver)
        }
    }

    public fun computeSuggestionsForName(name: Name, callTypeAndReceiver: CallTypeAndReceiver<out JetElement?, *>):
            Collection<DeclarationDescriptor> {
        val nameStr = name.asString()
        if (nameStr.isEmpty()) return emptyList()

        val file = element.getContainingFile() as JetFile

        fun filterByCallType(descriptor: DeclarationDescriptor) = callTypeAndReceiver.callType.descriptorKindFilter.accepts(descriptor)

        val searchScope = getResolveScope(file)

        val bindingContext = element.analyze(BodyResolveMode.PARTIAL)

        val diagnostics = bindingContext.getDiagnostics().forElement(element)

        if (!diagnostics.any { it.getFactory() in getSupportedErrors() }) return emptyList()

        val resolutionScope = element.getResolutionScope(bindingContext, file.getResolutionFacade())
        val containingDescriptor = resolutionScope.ownerDescriptor

        fun isVisible(descriptor: DeclarationDescriptor): Boolean {
            if (descriptor is DeclarationDescriptorWithVisibility) {
                return descriptor.isVisible(containingDescriptor, bindingContext, element as? JetSimpleNameExpression)
            }

            return true
        }

        val result = ArrayList<DeclarationDescriptor>()

        val indicesHelper = KotlinIndicesHelper(element.getResolutionFacade(), searchScope, ::isVisible, true)

        val expression = element
        if (expression is JetSimpleNameExpression) {
            if (!expression.isImportDirectiveExpression() && !JetPsiUtil.isSelectorInQualified(expression)) {
                if (ProjectStructureUtil.isJsKotlinModule(file)) {
                    indicesHelper.getKotlinClasses({ it == nameStr }, { true }).filterTo(result, ::filterByCallType)

                } else {
                    indicesHelper.getJvmClassesByName(nameStr).filterTo(result, ::filterByCallType)
                }

                indicesHelper.getTopLevelCallablesByName(nameStr).filterTo(result, ::filterByCallType)
            }
        }

        result.addAll(indicesHelper.getCallableTopLevelExtensions({ it == nameStr }, callTypeAndReceiver, element, bindingContext))

        return if (result.size > 1)
            reduceCandidatesBasedOnDependencyRuleViolation(result, file)
        else
            result
    }

    private fun reduceCandidatesBasedOnDependencyRuleViolation(
            candidates: Collection<DeclarationDescriptor>, file: PsiFile): Collection<DeclarationDescriptor> {
        val project = file.project
        val validationManager = DependencyValidationManager.getInstance(project)
        return candidates.filter {
            val targetFile = DescriptorToSourceUtilsIde.getAnyDeclaration(project, it)?.containingFile ?: return@filter true
            validationManager.getViolatorDependencyRules(file, targetFile).isEmpty()
        }
    }
}

class AutoImportFix(expression: JetSimpleNameExpression, diagnostic: Diagnostic? = null) :
        AutoImportFixBase<JetSimpleNameExpression>(expression, diagnostic) {
    override fun getCallTypeAndReceiver() = CallTypeAndReceiver.detect(element)

    override fun getImportNames(): Collection<Name> {
        if (element.getIdentifier() == null) {
            val conventionName = JetPsiUtil.getConventionName(element)
            if (conventionName != null) {
                if (element is JetOperationReferenceExpression) {
                    val elementType = element.firstChild.node.elementType
                    if (OperatorConventions.ASSIGNMENT_OPERATIONS.containsKey(elementType)) {
                        val conterpart = OperatorConventions.ASSIGNMENT_OPERATION_COUNTERPARTS.get(elementType)
                        val counterpartName = OperatorConventions.BINARY_OPERATION_NAMES.get(conterpart)
                        if (counterpartName != null) {
                            return listOf(conventionName, counterpartName)
                        }
                    }
                }

                return conventionName.singletonOrEmptyList()
            }
        }
        else if (Name.isValidIdentifier(element.getReferencedName())) {
            return Name.identifier(element.getReferencedName()).singletonOrEmptyList()
        }

        return emptyList()
    }

    override fun getSupportedErrors() = ERRORS

    companion object : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic) =
                (diagnostic.getPsiElement() as? JetSimpleNameExpression)?.let { AutoImportFix(it, diagnostic) }

        override fun isApplicableForCodeFragment() = true

        private val ERRORS: Collection<DiagnosticFactory<*>> by lazy(LazyThreadSafetyMode.PUBLICATION) { QuickFixes.getInstance().getDiagnostics(this) }
    }
}

class MissingInvokeAutoImportFix(expression: JetExpression, diagnostic: Diagnostic) :
        AutoImportFixBase<JetExpression>(expression, diagnostic) {
    override fun getImportNames() = OperatorNameConventions.INVOKE.singletonList()

    override fun getCallTypeAndReceiver() = CallTypeAndReceiver.OPERATOR(element)

    override fun getSupportedErrors() = ERRORS

    companion object : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic) =
                (diagnostic.psiElement as? JetExpression)?.let { MissingInvokeAutoImportFix(it, diagnostic) }

        private val ERRORS by lazy(LazyThreadSafetyMode.PUBLICATION) { QuickFixes.getInstance().getDiagnostics(this) }
    }
}

class MissingArrayAccessorAutoImportFix(element: JetArrayAccessExpression, diagnostic: Diagnostic) :
        AutoImportFixBase<JetArrayAccessExpression>(element, diagnostic) {
    override fun getImportNames() =
            (if ((element.parent as? JetBinaryExpression)?.operationToken == JetTokens.EQ)
                OperatorNameConventions.SET
            else
                OperatorNameConventions.SET).singletonList()

    override fun getCallTypeAndReceiver() =
            CallTypeAndReceiver.OPERATOR((element).arrayExpression!!)

    override fun getSupportedErrors() = ERRORS

    companion object : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): JetIntentionAction<JetArrayAccessExpression>? {
            val element = diagnostic.psiElement
            if (element is JetArrayAccessExpression && element.arrayExpression != null) {
                return MissingArrayAccessorAutoImportFix(element, diagnostic)
            }

            return null
        }

        private val ERRORS by lazy(LazyThreadSafetyMode.PUBLICATION) { QuickFixes.getInstance().getDiagnostics(this) }
    }
}

class MissingDelegateAccessorsAutoImportFix(element: JetExpression, diagnostics: Collection<Diagnostic>) :
        AutoImportFixBase<JetExpression>(element, diagnostics) {
    override fun createAction(project: Project, editor: Editor): KotlinAddImportAction {
        return KotlinAddImportAction(project, editor, element, suggestions)
    }

    override fun getImportNames(): Collection<Name> {
        return diagnostics.map {
            if (it.toString().contains(OperatorNameConventions.GET_VALUE.asString()))
                OperatorNameConventions.GET_VALUE
            else
                OperatorNameConventions.SET_VALUE
        }.distinct()
    }

    override fun getCallTypeAndReceiver() = CallTypeAndReceiver.DELEGATE(element)

    override fun getSupportedErrors() = ERRORS

    companion object : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): JetIntentionAction<JetExpression>? {
            assert(diagnostic.factory == Errors.DELEGATE_SPECIAL_FUNCTION_MISSING)
            return MissingDelegateAccessorsAutoImportFix(diagnostic.psiElement as JetExpression, listOf(diagnostic))
        }

        private val ERRORS by lazy(LazyThreadSafetyMode.PUBLICATION) { QuickFixes.getInstance().getDiagnostics(this) }
    }
}

class MissingComponentsAutoImportFix(element: JetExpression, diagnostics: Collection<Diagnostic>) :
        AutoImportFixBase<JetExpression>(element, diagnostics) {
    override fun createAction(project: Project, editor: Editor) = KotlinAddImportAction(project, editor, element, suggestions)

    override fun getImportNames() = diagnostics.map { Name.identifier(Errors.COMPONENT_FUNCTION_MISSING.cast(it).a.identifier) }

    override fun getCallTypeAndReceiver() = CallTypeAndReceiver.OPERATOR(element)

    override fun getSupportedErrors() = ERRORS

    companion object : JetSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): JetIntentionAction<JetExpression>? {
            return MissingComponentsAutoImportFix(diagnostic.psiElement as JetExpression, listOf(diagnostic))
        }

        private val ERRORS by lazy(LazyThreadSafetyMode.PUBLICATION) { QuickFixes.getInstance().getDiagnostics(this) }
    }
}
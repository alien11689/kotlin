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

package org.jetbrains.kotlin.idea.actions

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.util.ProximityLocation
import com.intellij.psi.util.proximity.PsiProximityComparator
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.idea.JetBundle
import org.jetbrains.kotlin.idea.JetDescriptorIconProvider
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.ImportableFqNameClassifier
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.references.JetSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.ImportInsertHelper
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.JetElement
import org.jetbrains.kotlin.psi.JetFile
import org.jetbrains.kotlin.psi.JetSimpleNameExpression
import org.jetbrains.kotlin.resolve.dataClassUtils.isComponentLike

/**
 * Automatically adds import directive to the file for resolving reference.
 * Based on {@link AddImportAction}
 */
public class KotlinAddImportAction(
        private val project: Project,
        private val editor: Editor,
        private val element: JetElement,

        candidates: Collection<DeclarationDescriptor>
) : QuestionAction {

    private val file = element.getContainingJetFile()
    private val prioritizer = Prioritizer(file)

    private inner class SingleImportVariant(val fqName: FqName, val descriptors: Collection<DeclarationDescriptor>, val all: Boolean = false) {
        val priority = descriptors
                .map { prioritizer.priority(fqName, it) }
                .min()!!

        val descriptorsToImport: Collection<DeclarationDescriptor> get() =
                if (all) {
                    descriptors
                }
                else {
                    listOf(descriptors.singleOrNull() ?: descriptors.sortedBy { if (it is ClassDescriptor) 0 else 1 }.first())
                }

        val hint: String get() = if (all) "Components in $fqName" else fqName.asString()


        val declarationToImport: PsiElement? = DescriptorToSourceUtilsIde.getAnyDeclaration(project, descriptorsToImport.first())
    }

    internal fun showHint(): Boolean {
        if (variants.isEmpty()) return false

        val hintText = ShowAutoImportPass.getMessage(variants.size > 1, highestPriorityVariantHint)
        HintManager.getInstance().showQuestionHint(editor, hintText, element.getTextOffset(), element.getTextRange()!!.getEndOffset(), this)

        return true
    }

    private val variants: List<SingleImportVariant> =
//            candidates
//                    .groupBy { it.importableFqName?.parentOrNull() ?: FqName.ROOT }
//                    .filter { it.value.all { candidate -> isComponentLike(candidate.name) } }
//                    .map { SingleImportVariant(it.key.parentOrNull() ?: FqName.ROOT, it.value, true) } +
            candidates
                    .groupBy { it.importableFqName!! }
                    .map { SingleImportVariant(it.key, it.value) }
                    .sortedBy { it.priority }

    public val highestPriorityVariantHint: String get() = variants.first().hint

    override fun execute(): Boolean {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        if (!element.isValid) return false

        if (variants.size() == 1 || ApplicationManager.getApplication().isUnitTestMode) {
            addImport(variants.first())
        }
        else {
            chooseCandidateAndImport()
        }

        return true
    }

    protected fun getVariantSelectionPopup(): BaseListPopupStep<SingleImportVariant> {
        return object : BaseListPopupStep<SingleImportVariant>(JetBundle.message("imports.chooser.title"), variants) {
            override fun isAutoSelectionEnabled() = false

            override fun onChosen(selectedValue: SingleImportVariant?, finalChoice: Boolean): PopupStep<String>? {
                if (selectedValue == null || project.isDisposed) return null

                if (finalChoice) {
                    addImport(selectedValue)
                    return null
                }

                val toExclude = AddImportAction.getAllExcludableStrings(selectedValue.fqName.asString())

                return object : BaseListPopupStep<String>(null, toExclude) {
                    override fun getTextFor(value: String): String {
                        return "Exclude '$value' from auto-import"
                    }

                    override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<Any>? {
                        if (finalChoice && !project.isDisposed) {
                            AddImportAction.excludeFromImport(project, selectedValue)
                        }
                        return null
                    }
                }
            }

            override fun hasSubstep(selectedValue: SingleImportVariant?) = true

            override fun getTextFor(value: SingleImportVariant) = value.fqName.asString()

            override fun getIconFor(value: SingleImportVariant) = JetDescriptorIconProvider.getIcon(
                    value.descriptorsToImport.first(),
                    value.declarationToImport,
                    0)
        }
    }

    private fun chooseCandidateAndImport() {
        JBPopupFactory.getInstance().createListPopup(getVariantSelectionPopup()).showInBestPositionFor(editor)
    }

    private fun addImport(selectedVariant: SingleImportVariant) {
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        project.executeWriteCommand(QuickFixBundle.message("add.import")) {
            if (!element.isValid) return@executeWriteCommand

            selectedVariant.declarationToImport?.let {
                val location = ProximityLocation(file, ModuleUtilCore.findModuleForPsiElement(file))
                StatisticsManager.getInstance().incUseCount(PsiProximityComparator.STATISTICS_KEY, it, location)
            }

            for (descriptor in selectedVariant.descriptorsToImport) {
                // for class or package we use ShortenReferences because we not necessary insert an import but may want to
                // insert partly qualified name
                if (descriptor is ClassDescriptor || descriptor is PackageViewDescriptor) {
                    if (element is JetSimpleNameExpression) {
                        element.mainReference.bindToFqName(descriptor.importableFqName!!, JetSimpleNameReference.ShorteningMode.FORCED_SHORTENING)
                    }
                } else {
                    ImportInsertHelper.getInstance(project).importDescriptor(file, descriptor)
                }
            }
        }
    }

    private class Prioritizer(private val file: JetFile) {
        private val classifier = ImportableFqNameClassifier(file)
        private val proximityComparator = PsiProximityComparator(file)

        private inner class Priority(private val fqName: FqName, private val descriptor: DeclarationDescriptor) : Comparable<Priority> {
            private val isDeprecated = KotlinBuiltIns.isDeprecated(descriptor)
            private val classification = classifier.classify(fqName, false)
            private val declaration = DescriptorToSourceUtilsIde.getAnyDeclaration(file.project, descriptor)

            override fun compareTo(other: Priority): Int {
                if (isDeprecated != other.isDeprecated) {
                    return if (isDeprecated) +1 else -1
                }

                val c1 = classification.compareTo(other.classification)
                if (c1 != 0) return c1

                val c2 = proximityComparator.compare(declaration, other.declaration)
                if (c2 != 0) return c2

                return fqName.asString().compareTo(other.fqName.asString())
            }
        }

        fun priority(fqName: FqName, descriptor: DeclarationDescriptor) = Priority(fqName, descriptor)
    }
}

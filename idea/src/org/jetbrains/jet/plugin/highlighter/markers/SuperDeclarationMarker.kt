/*
 * Copyright 2010-2014 JetBrains s.r.o.
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

package org.jetbrains.jet.plugin.highlighter.markers

import org.jetbrains.jet.lang.psi.JetElement
import org.jetbrains.jet.lang.resolve.BindingContext
import org.jetbrains.jet.plugin.project.AnalyzerFacadeWithCache
import org.jetbrains.jet.lang.descriptors.CallableMemberDescriptor
import org.jetbrains.jet.lang.resolve.OverrideResolver
import org.jetbrains.jet.lang.descriptors.Modality
import org.jetbrains.jet.renderer.DescriptorRenderer
import org.jetbrains.jet.renderer.DescriptorRendererBuilder
import org.jetbrains.jet.lang.descriptors.PropertyAccessorDescriptor
import org.jetbrains.jet.lang.descriptors.PropertyDescriptor
import com.intellij.psi.NavigatablePsiElement
import java.awt.event.MouseEvent
import org.jetbrains.jet.plugin.codeInsight.DescriptorToDeclarationUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import org.jetbrains.jet.plugin.JetBundle
import org.jetbrains.jet.plugin.codeInsight.JetFunctionPsiElementCellRenderer
import org.jetbrains.annotations.TestOnly
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import java.util.ArrayList
import com.intellij.util.Function

object SuperDeclarationMarkerTooltip: Function<JetElement, String> {
    override fun `fun`(param: JetElement?): String? {
        val element = param!!

        [suppress("UNUSED_VARIABLE")]
        val (elementDescriptor, _, overriddenDescriptors) = resolveDeclarationWithParents(element)
        if (overriddenDescriptors.isEmpty()) return ""

        val isAbstract = elementDescriptor!!.getModality() == Modality.ABSTRACT

        val renderer = DescriptorRendererBuilder()
                .setTextFormat(DescriptorRenderer.TextFormat.HTML)
                .setWithDefinedIn(false)
                .setStartFromName(true)
                .setWithoutSuperTypes(true)
                .build()

        val containingStrings = overriddenDescriptors.map {
            val declaration = it.getContainingDeclaration()
            val memberKind = if (it is PropertyAccessorDescriptor || it is PropertyDescriptor) "property" else "function"

            val isBaseAbstract = it.getModality() == Modality.ABSTRACT
            "${if (!isAbstract && isBaseAbstract) "Implements" else "Overrides"} $memberKind in '${renderer.render(declaration)}'"
        }

        return containingStrings.sort().join(separator = "<br/>")
    }
}

public class SuperDeclarationMarkerNavigationHandler : GutterIconNavigationHandler<JetElement> {
    private var testNavigableElements: List<NavigatablePsiElement>? = null

    TestOnly
    public fun getNavigationElements(): List<NavigatablePsiElement> {
        val navigationResult = testNavigableElements!!
        testNavigableElements = null
        return navigationResult
    }

    override fun navigate(e: MouseEvent?, element: JetElement?) {
        if (element == null) return

        val (elementDescriptor, bindingContext, overriddenDescriptors) = resolveDeclarationWithParents(element)
        if (overriddenDescriptors.isEmpty()) return

        val superDeclarations = ArrayList<NavigatablePsiElement>()
        for (overriddenMember in overriddenDescriptors) {
            val declarations = DescriptorToDeclarationUtil.resolveToPsiElements(element.getProject(), overriddenMember)
            for (declaration in declarations) {
                if (declaration is NavigatablePsiElement) {
                    superDeclarations.add(declaration as NavigatablePsiElement)
                }
            }
        }

        if (!ApplicationManager.getApplication()!!.isUnitTestMode()) {
            val elementName = elementDescriptor!!.getName()

            PsiElementListNavigator.openTargets(
                    e,
                    superDeclarations.copyToArray(),
                    JetBundle.message("navigation.title.super.declaration", elementName),
                    JetBundle.message("navigation.findUsages.title.super.declaration", elementName),
                    JetFunctionPsiElementCellRenderer(bindingContext))
        }
        else {
            // Only store elements for retrieve in test
            testNavigableElements = superDeclarations
        }
    }
}

public data class ResolveWithParentsResult(
        val descriptor: CallableMemberDescriptor?,
        val bindingContext: BindingContext,
        val overriddenDescriptors: Collection<CallableMemberDescriptor>)

public fun resolveDeclarationWithParents(element: JetElement): ResolveWithParentsResult {
    val bindingContext = AnalyzerFacadeWithCache.getContextForElement(element)
    val descriptor = bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, element)

    if (descriptor !is CallableMemberDescriptor) return ResolveWithParentsResult(null, bindingContext, listOf())

    val overriddenMembers = OverrideResolver.getDirectlyOverriddenDeclarations(descriptor)
    return ResolveWithParentsResult(descriptor, bindingContext, overriddenMembers)
}

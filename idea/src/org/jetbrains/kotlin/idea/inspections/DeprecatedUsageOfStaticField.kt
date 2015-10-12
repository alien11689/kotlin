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

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.kotlin.asJava.KotlinLightClass
import org.jetbrains.kotlin.asJava.KotlinLightFieldForDeclaration
import org.jetbrains.kotlin.idea.quickfix.AddConstModifierFix
import org.jetbrains.kotlin.idea.quickfix.AddConstModifierIntention
import org.jetbrains.kotlin.idea.quickfix.replaceReferencesToGetterByReferenceToField
import org.jetbrains.kotlin.lexer.JetTokens
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.JetClass
import org.jetbrains.kotlin.psi.JetObjectDeclaration
import org.jetbrains.kotlin.psi.JetProperty
import org.jetbrains.kotlin.psi.JetPsiFactory
import java.util.*

class DeprecatedUsageOfStaticFieldInspection : LocalInspectionTool(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                val resolvedTo = expression.reference?.resolve() as? PsiField ?: return
                if (!resolvedTo.hasModifierProperty(PsiModifier.STATIC) || !resolvedTo.isDeprecated) return

                val kotlinProperty = (resolvedTo as? KotlinLightFieldForDeclaration)?.getOrigin() as? JetProperty

                // NOTE: this is hack to avoid test failing with "action is still available" error
                if (kotlinProperty?.hasJvmFieldAnnotationOrConstModifier() ?: false) return

                val kotlinClassOrObject = (resolvedTo.containingClass as? KotlinLightClass)?.getOrigin() ?: return

                val containingObject = when (kotlinClassOrObject) {
                    is JetObjectDeclaration -> kotlinClassOrObject as JetObjectDeclaration // KT-9578
                    is JetClass -> kotlinClassOrObject.getCompanionObjects().singleOrNull() ?: return
                    else -> return
                }
                holder.registerProblem(
                        expression, "This field will not be generated in future versions of Kotlin. Use 'const' modifier, '@JvmField' annotation or access data through corresponding object.",
                        ProblemHighlightType.LIKE_DEPRECATED,
                        *createFixes(containingObject, kotlinProperty).toTypedArray()
                )
            }
        }
    }


    private fun createFixes(containingObject: JetObjectDeclaration, property: JetProperty?): List<LocalQuickFix> {
        if (containingObject.getContainingJetFile().isCompiled) return listOf(ReplaceWithGetterInvocationFix())

        // order matters here, 'cleanup' applies fixes in this order
        val fixes = ArrayList<LocalQuickFix>()
        if (property != null && AddConstModifierIntention.isApplicableTo(property)) {
            fixes.add(AddConstModifierLocalFix())
        }

        if (containingObject.isCompanion()) {
            val classWithCompanion = containingObject.parent?.parent as? JetClass ?: return listOf()
            if (!classWithCompanion.isInterface()) {
                fixes.add(AddJvmFieldAnnotationFix())
            }
        } else {
            fixes.add(AddJvmFieldAnnotationFix())
        }

        fixes.add(ReplaceWithGetterInvocationFix())

        return fixes
    }
}

class AddJvmFieldAnnotationFix : LocalQuickFix {
    override fun getName(): String = "Annotate property with @JvmField"
    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val deprecatedField = descriptor.psiElement.reference?.resolve() ?: return
        val kotlinProperty = (deprecatedField as? KotlinLightFieldForDeclaration)?.getOrigin() as? JetProperty ?: return

        if (kotlinProperty.hasJvmFieldAnnotationOrConstModifier()) return

        replaceReferencesToGetterByReferenceToField(kotlinProperty)

        kotlinProperty.addAnnotationEntry(JetPsiFactory(project).createAnnotationEntry("@JvmField"))
    }
}

class AddConstModifierLocalFix : LocalQuickFix {
    override fun getName(): String = "Add 'const' modifier to a property"
    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val deprecatedField = descriptor.psiElement.reference?.resolve() ?: return
        val kotlinProperty = (deprecatedField as? KotlinLightFieldForDeclaration)?.getOrigin() as? JetProperty ?: return

        if (kotlinProperty.hasJvmFieldAnnotationOrConstModifier()) return

        AddConstModifierFix.addConstModifier(kotlinProperty)
    }
}

class ReplaceWithGetterInvocationFix : LocalQuickFix {
    override fun getName(): String = "Replace with getter invocation"
    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val deprecatedField = descriptor.psiElement.reference?.resolve() as? PsiField ?: return
        val kotlinProperty = (deprecatedField as? KotlinLightFieldForDeclaration)?.getOrigin() as? JetProperty

        //NOTE: fix should no longer be available in this case
        if (kotlinProperty != null && kotlinProperty.hasJvmFieldAnnotationOrConstModifier()) return

        val lightClass = deprecatedField.containingClass as? KotlinLightClass ?: return

        fun replaceWithGetterInvocation(objectField: PsiField) {
            val factory = PsiElementFactory.SERVICE.getInstance(project)
            val elementToReplace = descriptor.psiElement

            val getterInvocation = factory.createExpressionFromText(
                    objectField.containingClass!!.qualifiedName + "." + objectField.name + "." + JvmAbi.getterName(deprecatedField.name!!) + "()",
                    elementToReplace
            )
            elementToReplace.replace(getterInvocation)
        }

        val kotlinClass = lightClass.getOrigin()
        when (kotlinClass) {
            is JetObjectDeclaration -> {
                val instanceField = lightClass.findFieldByName(JvmAbi.INSTANCE_FIELD, false) ?: return
                replaceWithGetterInvocation(instanceField)
            }
            is JetClass -> {
                val companionObjectName = kotlinClass.getCompanionObjects().singleOrNull()?.name ?: return
                val companionObjectField = lightClass.findFieldByName(companionObjectName, false) ?: return
                replaceWithGetterInvocation(companionObjectField)
            }
        }
    }
}

private fun JetProperty.hasJvmFieldAnnotationOrConstModifier(): Boolean {
    return hasModifier(JetTokens.CONST_KEYWORD) || annotationEntries.any { it.text == "@JvmField" }
}
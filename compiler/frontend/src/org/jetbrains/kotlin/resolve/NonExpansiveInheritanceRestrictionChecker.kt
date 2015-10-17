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

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.JetClass
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.DFS

public object NonExpansiveInheritanceRestrictionChecker {
    @JvmStatic
    fun check(
            declaration: JetClass,
            type: JetType,
            diagnosticHolder: DiagnosticSink
    ) {

        if (type.constructor.parameters.isEmpty()) return

        val (expansiveEdges, edgeLists) = buildGraph(type)

        for ((first, second) in expansiveEdges) {
            val reachable = collectReachable(second, edgeLists)
            if (first in reachable) {
                val typeParameter = reachable.firstOrNull { it in type.constructor.parameters } ?: first
                val element = DescriptorToSourceUtils.descriptorToDeclaration(typeParameter) ?: declaration
                diagnosticHolder.report(Errors.EXPANSIVE_INHERITANCE.on(element))
                break;
            }
        }
    }

    private fun buildGraph(type: JetType): Pair<Set<Pair<TypeParameterDescriptor, TypeParameterDescriptor>>, Map<TypeParameterDescriptor, Set<TypeParameterDescriptor>>> {
        val processedTypes = hashSetOf<JetType>()

        val expansiveEdges = linkedSetOf<Pair<TypeParameterDescriptor, TypeParameterDescriptor>>()
        val edgeLists = hashMapOf<TypeParameterDescriptor, MutableSet<TypeParameterDescriptor>>()

        doBuildGraph(type, processedTypes, expansiveEdges, edgeLists)
        return Pair(expansiveEdges, edgeLists)
    }

    private fun doBuildGraph(
            type: JetType,
            processedTypes: MutableSet<JetType>,
            expansiveEdges: MutableSet<Pair<TypeParameterDescriptor, TypeParameterDescriptor>>,
            edgeLists: MutableMap<TypeParameterDescriptor, MutableSet<TypeParameterDescriptor>>
    ) {

        fun addEdge(from: TypeParameterDescriptor, to: TypeParameterDescriptor, expansive: Boolean = false) {
            val list = edgeLists.getOrPut(from) { linkedSetOf() }
            list.add(to)
            if (expansive) {
                expansiveEdges.add(Pair(from, to))
            }
        }

        val typeConstructor = type.constructor
        if (typeConstructor.parameters.isEmpty()) return

        val typeParameters = typeConstructor.parameters

        // For each type parameter T, let ST be the set of all constituent types of all immediate supertypes of the owner of T.
        // If T appears as a constituent type of a simple type argument A in a generic type in ST, add an edge from T
        // to U, where U is the type parameter corresponding to A, and where the edge is non-expansive if A has the form T or T?,
        // the edge is expansive otherwise.
        for (constituentType in TypeUtils.constituentTypes(typeConstructor.supertypes)) {
            constituentType.arguments.forEachIndexed { i, typeProjection ->
                if (typeProjection.projectionKind == Variance.INVARIANT) {
                    val constituents = TypeUtils.constituentTypes(setOf(typeProjection.type))

                    for (typeParameter in typeParameters) {
                        if (constituentType.constructor.parameters.size > i &&
                                (typeParameter.defaultType in constituents || TypeUtils.makeNullable(typeParameter.defaultType) in constituents)) {
                            addEdge(typeParameter, constituentType.constructor.parameters[i], !TypeUtils.isTypeParameter(typeProjection.type))
                        }
                    }
                }
                else {
                    // Furthermore, if T appears as a constituent type of an element of the B-closure of the set of lower and
                    // upper bounds of a skolem type variable Q in a skolemization of a projected generic type in ST, add an
                    // expanding edge from T to V, where V is the type parameter corresponding to Q.
                    if (constituentType.constructor.parameters.size > i) {
                        val originalTypeParameter = constituentType.constructor.parameters[i]
                        val bounds = hashSetOf<JetType>()

                        val substitutor = constituentType.substitution.buildSubstitutor()
                        val adaptedUpperBounds = originalTypeParameter.upperBounds.map { substitutor.substitute(it, Variance.INVARIANT) }.filterNotNull()
                        bounds.addAll(adaptedUpperBounds)

                        if (!typeProjection.isStarProjection) {
                            bounds.add(typeProjection.type)
                        }

                        val boundClosure = TypeUtils.boundClosure(bounds)
                        val constituentTypes = TypeUtils.constituentTypes(boundClosure)
                        for (typeParameter in typeParameters) {
                            if (typeParameter.defaultType in constituentTypes || TypeUtils.makeNullable(typeParameter.defaultType) in constituentTypes) {
                                addEdge(typeParameter, constituentType.constructor.parameters[i], true)
                            }
                        }
                    }
                }
            }
            if (constituentType !in processedTypes) {
                processedTypes.add(constituentType)
                doBuildGraph(constituentType, processedTypes, expansiveEdges, edgeLists)
            }
        }
    }

    private fun <T> collectReachable(from: T, edgeLists: Map<T, Set<T>>): List<T> {
        val handler = object : DFS.NodeHandlerWithListResult<T, T>() {
            override fun afterChildren(current: T?) {
                result.add(current)
            }
        }

        val neighbors = object : DFS.Neighbors<T> {
            override fun getNeighbors(current: T?): Iterable<T> {
                return if (current != null) edgeLists[current] ?: emptyList() else emptyList()
            }
        }

        DFS.dfs(listOf(from), neighbors, handler)

        return handler.result()
    }
}

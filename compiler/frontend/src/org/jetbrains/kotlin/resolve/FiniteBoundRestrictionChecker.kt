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

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.JetClass
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.DFS

public object FiniteBoundRestrictionChecker {
    @JvmStatic
    fun check(
            declaration: JetClass,
            type: JetType,
            diagnosticHolder: DiagnosticSink
    ) {
        if (type.constructor.parameters.isEmpty()) return

        // For every projection type argument A in every generic type B<…> in the set of constituent types
        // of every type in the B-closure the set of declared upper bounds of every type parameter T add an
        // edge from T to U, where U is the type parameter of the declaration of B<…> corresponding to the type argument A.
        // It is a compile-time error if the graph G has a cycle.
        val (nodes, edgeLists) = buildGraph(type)

        for (node in nodes) {
            if (isInCycle(node, edgeLists)) {
                val element = DescriptorToSourceUtils.descriptorToDeclaration(node) ?: declaration
                diagnosticHolder.report(Errors.FINITE_BOUNDS_VIOLATION.on(element))
                break;
            }
        }
    }

    private fun buildGraph(type: JetType): Pair<Set<TypeParameterDescriptor>, Map<TypeParameterDescriptor, List<TypeParameterDescriptor>>> {
        val nodes = linkedSetOf<TypeParameterDescriptor>()
        val edgeLists = hashMapOf<TypeParameterDescriptor, MutableList<TypeParameterDescriptor>>()
        val processedTypes = hashSetOf<JetType>()

        doBuildGraph(type, processedTypes, nodes, edgeLists)
        return Pair(nodes, edgeLists)
    }

    private fun doBuildGraph(
            type: JetType,
            processedTypes: MutableSet<JetType>,
            nodes: MutableSet<TypeParameterDescriptor>,
            edgeLists: MutableMap<TypeParameterDescriptor, MutableList<TypeParameterDescriptor>>
    ) {
        type.constructor.parameters.forEach { typeParameter ->
            nodes.add(typeParameter)
            val boundClosure = TypeUtils.boundClosure(typeParameter.upperBounds)
            val constituentTypes = TypeUtils.constituentTypes(boundClosure)
            constituentTypes.forEach { constituentType ->
                constituentType.arguments.forEachIndexed { i, typeProjection ->
                    if (typeProjection.projectionKind != Variance.INVARIANT) {
                        if (constituentType.constructor.parameters.size > i) {
                            val otherTypeParameter = constituentType.constructor.parameters[i]
                            val list = edgeLists.getOrPut(typeParameter) { arrayListOf() }
                            list.add(otherTypeParameter)
                            if (constituentType !in processedTypes) {
                                processedTypes.add(constituentType)
                                doBuildGraph(constituentType, processedTypes, nodes, edgeLists)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun <T> isInCycle(from: T, edgeLists: Map<T, List<T>>): Boolean {
        var result = false

        val visited = object : DFS.VisitedWithSet<T>() {
            override fun checkAndMarkVisited(current: T): Boolean {
                val added = super.checkAndMarkVisited(current)
                if (!added && current == from) {
                    result = true
                }
                return added
            }

        }

        val handler = object : DFS.AbstractNodeHandler<T, Unit>() {
            override fun result() {}
        }

        val neighbors = object : DFS.Neighbors<T> {
            override fun getNeighbors(current: T?) = if (current != null) edgeLists[current] ?: emptyList() else emptyList()
        }

        DFS.dfs(listOf(from), neighbors, visited, handler)

        return result
    }
}

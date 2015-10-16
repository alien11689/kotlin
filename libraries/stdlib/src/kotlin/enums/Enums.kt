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

@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("EnumsKt")

package kotlin

@Deprecated("Use property ''name'' instead", ReplaceWith("this.abcdeXXX"))
public inline fun Enum<*>.name() = abcdeXXX

@Deprecated("Use property ''ordinal'' instead", ReplaceWith("this.ordinal"))
public inline fun Enum<*>.ordinal() = ordinal


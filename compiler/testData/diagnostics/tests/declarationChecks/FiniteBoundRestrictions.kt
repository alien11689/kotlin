interface A0<T : A0<T>>
interface A1<<!FINITE_BOUNDS_VIOLATION!>T : A1<*><!>>
interface A2<<!FINITE_BOUNDS_VIOLATION!>T : A2<out T><!>>
// StackOverflowError
//interface A3<T : A3<in T>>
interface A4<<!FINITE_BOUNDS_VIOLATION!>T : A4<*>?<!>>

interface B0<<!FINITE_BOUNDS_VIOLATION!>T : B1<*><!>>
interface B1<<!FINITE_BOUNDS_VIOLATION!>T : B0<*><!>>

interface C0<T, S : C0<*, S>>

interface C1<T : C1<T, *>, <!FINITE_BOUNDS_VIOLATION!>S : C1<S, *><!>>   // T -> S, S -> S
interface C2<<!FINITE_BOUNDS_VIOLATION!>T : C2<T, *><!>, S : C2<*, S>>   // T -> S, S -> T

interface D1<<!FINITE_BOUNDS_VIOLATION!>T<!>, U> where T : U, U: D1<*, U>
interface D2<<!FINITE_BOUNDS_VIOLATION!>T<!>, U> where T : U?, U: D2<*, *>
interface D3<<!FINITE_BOUNDS_VIOLATION!>T<!>, U, V> where T : U, U : V, V: D3<*, *, V>

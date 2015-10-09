// !DIAGNOSTICS: -UNUSED_PARAMETER

// FILE: A.java

public class A {
    public static int a = 1
    public static void foo() {}
    public static void baz(String s) {}
    private static void boo(int s) {}
}

// FILE: K.kt

open class K : A() {
    val a = 1
    <!ACCIDENTAL_OVERRIDE!>fun foo()<!> {}
    fun foo(i: Int) {}
    fun baz(i: Int) {}
    fun boo(i: Int) {}

    companion object {
        fun foo() {}
    }
}

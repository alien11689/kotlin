// !DIAGNOSTICS: -UNUSED_PARAMETER

// FILE: A.java

public class A {
    public static void foo() {}
    public static void baz(String s) {}
    private static void boo(int s) {}
}

// FILE: K.kt

open class K : A() {
    companion object {
        <!ACCIDENTAL_OVERRIDE!>@JvmStatic
        fun foo()<!> {}
        @JvmStatic
        fun foo(i: Int) {}
        @JvmStatic
        fun baz(i: Int) {}
        @JvmStatic
        fun boo(i: Int) {}
    }
}

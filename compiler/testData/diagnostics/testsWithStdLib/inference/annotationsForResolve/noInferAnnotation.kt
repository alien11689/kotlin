//!DIAGNOSTICS: -UNUSED_PARAMETER

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
fun <T> test1(t1: T, t2: @kotlin.internal.NoInfer T): T = t1

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
fun <T> @kotlin.internal.NoInfer T.test2(t1: T): T = t1

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
fun <T> test3(t1: @kotlin.internal.NoInfer T): T = t1

fun usage() {
    test1(1, <!TYPE_MISMATCH!>"312"<!>)
    <!TYPE_MISMATCH!>1<!>.test2("")
    <!TYPE_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>test3<!>("")
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
fun <T> List<T>.contains1(e: @kotlin.internal.NoInfer T): Boolean = true

fun test(l: List<Number>) {
    l.contains1(<!TYPE_MISMATCH!>""<!>)
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
fun <T> assertEquals1(e1: T, e2: @kotlin.internal.NoInfer T): Boolean = true

fun test(s: String) {
    assertEquals1(s, <!CONSTANT_EXPECTED_TYPE_MISMATCH!>11<!>)
}
fun foo(x: Unit) = x

fun test() {
    if (false);
    if (true);

    val x = <!IMPLICIT_CAST_TO_UNIT_OR_ANY, INVALID_IF_AS_EXPRESSION!>if (false)<!>;
    foo(x)

    val y: Unit = <!INVALID_IF_AS_EXPRESSION!>if (false)<!>;
    foo(y)

    foo({if (1==1);}())

    return <!INVALID_IF_AS_EXPRESSION!>if (true)<!>;
}
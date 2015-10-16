package foo

enum class EmptyEnum

enum class Simple {
    OK
}

enum class A {
    a() {
    },
    b(),
    c
}

enum class B {
    c
}
fun box(): String {
    if (Simple.OK.abcdeXXX != "OK") return "Simple.OK.abcdeXXX != OK, it: ${Simple.OK.abcdeXXX}"
    val ok = Simple.OK
    if (ok.ordinal != 0) return "ok = Simple.Ok; ok.ordinal != 0, it: ${ok.ordinal}"

    val ok2 = Simple.valueOf("OK")
    if (!ok2.equals(ok)) return "ok2 not equal ok"
    if (ok2.hashCode() != ok.hashCode()) return "hash(ok2) not equal hash(ok)"
    if (!ok2.identityEquals(ok)) return "ok2 not identity equal ok"


    if (EmptyEnum.values().size() != 0) return "EmptyEnum.values().size != 0"

    if (A.values() != arrayOf(A.a, A.b, A.c)) return "Wrong A.values(): " + A.values().toString()

    if (A.c.toString() != "c") return "A.c.toString() != c, it: ${A.c.toString()}"
    if (A.valueOf("b") != A.b) return "A.valueOf('b') != A.b"
    if (A.a == A.b) return "A.a == A.b"
    if (A.a.hashCode() == A.b.hashCode()) return "hash(A.a) == hash(A.b)"

    if (A.a.abcdeXXX != "a") return "A.a.abcdeXXX != a, it: ${A.a.abcdeXXX}"
    if (A.b.abcdeXXX != "b") return "A.b.abcdeXXX != b, it: ${A.b.abcdeXXX}"
    if (A.c.abcdeXXX != "c") return "A.c.abcdeXXX != c, it: ${A.c.abcdeXXX}"

    if (A.a.ordinal != 0) return "A.a.ordinal != 0, it: ${A.a.ordinal}"
    if (A.b.ordinal != 1) return "A.b.ordinal != 1, it: ${A.b.ordinal}"
    if (A.c.ordinal != 2) return "A.c.ordinal != 2, it: ${A.c.ordinal}"

    if (A.c.equals(B.c)) return "A.c.equals(B.c)"

    return "OK"
}
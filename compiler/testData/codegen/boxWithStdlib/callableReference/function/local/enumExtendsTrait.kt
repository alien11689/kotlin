interface Named {
    val abcdeXXX: String
}

enum class E : Named {
    OK
}

fun box(): String {
    return E.OK.abcdeXXX
}

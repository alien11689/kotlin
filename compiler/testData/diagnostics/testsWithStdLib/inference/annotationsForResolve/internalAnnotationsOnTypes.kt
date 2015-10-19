@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
public inline fun <reified R> Iterable<*>.filterIsInstance1(): List<@kotlin.internal.NoInfer R> = throw Exception()

fun test(list: List<Int>) {
    list.filterIsInstance1<Int>().map { it * 2}
}
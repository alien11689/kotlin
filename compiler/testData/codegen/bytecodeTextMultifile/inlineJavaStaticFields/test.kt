object KoKobject {
    @JvmStatic
    val JvmStatic: Int = 1
}

fun test() {
    Integer.MIN_VALUE
    java.lang.Long.MAX_VALUE

    JClass.PrimitiveInt
    JClass.BigPrimitiveInt
    JClass.PrimitiveByte
    JClass.PrimitiveChar
    JClass.Str

    JClass.BoxedInt
    JClass.NonFinal

    JClass().NonStatic

    KoKobject.JvmStatic
}

// @TestKt.class:
// 1 LDC -2147483648
// 1 LDC 9223372036854775807
// 1 SIPUSH 9000
// 1 LDC 59000
// 1 LDC -8
// 1 LDC K
// 1 LDC ":J"
// 1 GETSTATIC JClass.BoxedInt : Ljava/lang/Integer;
// 1 GETSTATIC JClass.NonFinal : I
// 1 GETFIELD JClass.NonStatic : I
// 1 INVOKESTATIC KoKobject.getJvmStatic \(\)I
package usage

import a.*

fun baz() {
    inlineFun {}
    inlineGetter
    inlineGetter = 1

    inlineSetter
    inlineSetter = 1

    allInline
    allInline = 1

    val a = Base()
    a.inlineFunBase {}
    a.inlineGetterBase
    a.inlineGetterBase = 1

    a.inlineSetterBase
    a.inlineSetterBase = 1

    a.allInlineBase
    a.allInlineBase = 1
}


class Derived : Base() {

    fun test() {
        inlineFunBase {}
        inlineGetterBase
        inlineGetterBase = 1

        inlineSetterBase
        inlineSetterBase = 1

        allInlineBase
        allInlineBase = 1
    }
}

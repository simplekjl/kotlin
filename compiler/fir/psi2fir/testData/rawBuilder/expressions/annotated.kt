@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
annotation class Ann

fun foo(arg: Int): Int {
    if (@Ann arg == 0) {
        @Ann return 1
    }
    @Ann if (arg == 1) {
        return (@Ann 1)
    }
    return 42
}
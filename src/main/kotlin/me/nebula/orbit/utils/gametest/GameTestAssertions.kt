package me.nebula.orbit.utils.gametest

infix fun Int.shouldBe(expected: Int) {
    if (this != expected) throw GameTestFailure("Expected <$expected> but was <$this>")
}

infix fun Double.shouldBe(expected: Double) {
    if (this != expected) throw GameTestFailure("Expected <$expected> but was <$this>")
}

infix fun Float.shouldBe(expected: Float) {
    if (this != expected) throw GameTestFailure("Expected <$expected> but was <$this>")
}

infix fun Int.shouldBeGreaterThan(expected: Int) {
    if (this <= expected) throw GameTestFailure("Expected <$this> to be greater than <$expected>")
}

infix fun Int.shouldBeLessThan(expected: Int) {
    if (this >= expected) throw GameTestFailure("Expected <$this> to be less than <$expected>")
}

infix fun Int.shouldBeAtLeast(expected: Int) {
    if (this < expected) throw GameTestFailure("Expected <$this> to be at least <$expected>")
}

infix fun Int.shouldBeAtMost(expected: Int) {
    if (this > expected) throw GameTestFailure("Expected <$this> to be at most <$expected>")
}

infix fun <T> T.shouldBe(expected: T) {
    if (this != expected) throw GameTestFailure("Expected <$expected> but was <$this>")
}

infix fun <T> T.shouldNotBe(expected: T) {
    if (this == expected) throw GameTestFailure("Expected value to differ from <$expected>")
}

fun <T> T?.shouldNotBeNull(): T {
    if (this == null) throw GameTestFailure("Expected non-null value but was null")
    return this
}

fun Boolean.shouldBeTrue(message: String = "") {
    if (!this) {
        val detail = if (message.isNotEmpty()) "$message: " else ""
        throw GameTestFailure("${detail}Expected true but was false")
    }
}

fun Boolean.shouldBeFalse(message: String = "") {
    if (this) {
        val detail = if (message.isNotEmpty()) "$message: " else ""
        throw GameTestFailure("${detail}Expected false but was true")
    }
}

fun <T> Collection<T>.shouldBeEmpty() {
    if (isNotEmpty()) throw GameTestFailure("Expected empty collection but had $size elements")
}

fun <T> Collection<T>.shouldNotBeEmpty() {
    if (isEmpty()) throw GameTestFailure("Expected non-empty collection but was empty")
}

fun <T> Collection<T>.shouldContain(element: T) {
    if (element !in this) throw GameTestFailure("Expected collection to contain <$element> but it did not")
}

infix fun <T> Collection<T>.shouldHaveSize(expected: Int) {
    if (size != expected) throw GameTestFailure("Expected collection size <$expected> but was <$size>")
}

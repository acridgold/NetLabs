package APICalling.api

import com.fasterxml.jackson.databind.JsonNode

class InvalidResponseException(message: String) : RuntimeException(message)

internal fun invalid(message: String): Nothing = throw InvalidResponseException(message)

internal fun JsonNode.requireObject(what: String = "ответ"): JsonNode {
    if (!isObject) invalid("$what не является JSON-объектом")
    return this
}

internal fun JsonNode.objectField(name: String): JsonNode =
    get(name)?.takeIf { it.isObject } ?: invalid("поле «$name» отсутствует или не объект")

internal fun JsonNode.arrayField(name: String): JsonNode =
    get(name)?.takeIf { it.isArray } ?: invalid("поле «$name» отсутствует или не массив")

internal fun JsonNode.numberField(name: String, range: ClosedFloatingPointRange<Double>): Double {
    val node = get(name)
    if (node == null || !node.isNumber) invalid("поле «$name» отсутствует или не число")
    return checkRange(name, node.doubleValue(), range)
}

internal fun JsonNode.optionalNumberField(name: String, range: ClosedFloatingPointRange<Double>): Double? {
    val node = get(name) ?: return null
    if (node.isNull) return null
    if (!node.isNumber) invalid("поле «$name» не число")
    return checkRange(name, node.doubleValue(), range)
}

private fun checkRange(name: String, value: Double, range: ClosedFloatingPointRange<Double>): Double {
    if (!value.isFinite() || value !in range) {
        invalid("поле «$name» вне допустимого диапазона ${range.start}..${range.endInclusive}: $value")
    }
    return value
}

internal fun JsonNode.longField(name: String, min: Long = 1): Long {
    val node = get(name)
    if (node == null || !node.isIntegralNumber) invalid("поле «$name» отсутствует или не целое число")
    val value = node.asLong()
    if (value < min) invalid("поле «$name» меньше допустимого ($min): $value")
    return value
}

internal fun JsonNode.optionalTextField(name: String): String? =
    get(name)?.takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.isNotEmpty() }

internal fun JsonNode.failIfMediaWikiError() {
    get("error")?.let { error ->
        throw IllegalStateException("Wikipedia: ${error.get("info")?.asText() ?: "ошибка запроса"}")
    }
}

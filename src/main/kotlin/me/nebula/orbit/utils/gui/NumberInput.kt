package me.nebula.orbit.utils.gui

import net.minestom.server.entity.Player
import net.minestom.server.item.Material

object NumberInput {

    fun openInt(
        player: Player,
        title: String,
        default: Int = 0,
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE,
        onCancel: () -> Unit = {},
        onInvalid: (String) -> Unit = {},
        onSubmit: (Int) -> Unit,
    ) {
        AnvilInput.open(
            player = player,
            title = title,
            default = default.toString(),
            iconMaterial = Material.PAPER,
            validator = { text ->
                val n = text.trim().toIntOrNull()
                when {
                    n == null -> AnvilValidation.Invalid(null, "Not an integer: '$text'")
                    n < min -> AnvilValidation.Invalid(null, "Must be >= $min")
                    n > max -> AnvilValidation.Invalid(null, "Must be <= $max")
                    else -> AnvilValidation.Valid
                }
            },
        ) { result ->
            when (result) {
                is AnvilResult.Submitted -> {
                    val parsed = result.text.trim().toIntOrNull()
                    if (parsed == null) onInvalid(result.text) else onSubmit(parsed.coerceIn(min, max))
                }
                AnvilResult.Cancelled -> onCancel()
            }
        }
    }

    fun openLong(
        player: Player,
        title: String,
        default: Long = 0L,
        min: Long = Long.MIN_VALUE,
        max: Long = Long.MAX_VALUE,
        onCancel: () -> Unit = {},
        onInvalid: (String) -> Unit = {},
        onSubmit: (Long) -> Unit,
    ) {
        AnvilInput.open(
            player = player,
            title = title,
            default = default.toString(),
            iconMaterial = Material.PAPER,
            validator = { text ->
                val n = text.trim().toLongOrNull()
                when {
                    n == null -> AnvilValidation.Invalid(null, "Not a long integer: '$text'")
                    n < min -> AnvilValidation.Invalid(null, "Must be >= $min")
                    n > max -> AnvilValidation.Invalid(null, "Must be <= $max")
                    else -> AnvilValidation.Valid
                }
            },
        ) { result ->
            when (result) {
                is AnvilResult.Submitted -> {
                    val parsed = result.text.trim().toLongOrNull()
                    if (parsed == null) onInvalid(result.text) else onSubmit(parsed.coerceIn(min, max))
                }
                AnvilResult.Cancelled -> onCancel()
            }
        }
    }

    fun openDouble(
        player: Player,
        title: String,
        default: Double = 0.0,
        min: Double = Double.NEGATIVE_INFINITY,
        max: Double = Double.POSITIVE_INFINITY,
        onCancel: () -> Unit = {},
        onInvalid: (String) -> Unit = {},
        onSubmit: (Double) -> Unit,
    ) {
        AnvilInput.open(
            player = player,
            title = title,
            default = default.toString(),
            iconMaterial = Material.PAPER,
            validator = { text ->
                val n = text.trim().toDoubleOrNull()
                when {
                    n == null -> AnvilValidation.Invalid(null, "Not a number: '$text'")
                    n.isNaN() -> AnvilValidation.Invalid(null, "NaN not allowed")
                    n < min -> AnvilValidation.Invalid(null, "Must be >= $min")
                    n > max -> AnvilValidation.Invalid(null, "Must be <= $max")
                    else -> AnvilValidation.Valid
                }
            },
        ) { result ->
            when (result) {
                is AnvilResult.Submitted -> {
                    val parsed = result.text.trim().toDoubleOrNull()
                    if (parsed == null) onInvalid(result.text) else onSubmit(parsed.coerceIn(min, max))
                }
                AnvilResult.Cancelled -> onCancel()
            }
        }
    }

    fun parseIntOrNull(text: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Int? {
        val n = text.trim().toIntOrNull() ?: return null
        if (n < min || n > max) return null
        return n
    }

    fun parseLongOrNull(text: String, min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): Long? {
        val n = text.trim().toLongOrNull() ?: return null
        if (n < min || n > max) return null
        return n
    }

    fun parseDoubleOrNull(text: String, min: Double = Double.NEGATIVE_INFINITY, max: Double = Double.POSITIVE_INFINITY): Double? {
        val n = text.trim().toDoubleOrNull() ?: return null
        if (n.isNaN() || n < min || n > max) return null
        return n
    }
}

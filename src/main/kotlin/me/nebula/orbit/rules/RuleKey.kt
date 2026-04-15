package me.nebula.orbit.rules

enum class RuleScope { INSTANCE, GAMEMODE, NETWORK }

class RuleKey<T : Any>(
    val id: String,
    val default: T,
    val scope: RuleScope,
) {
    override fun toString(): String = "RuleKey($id=$default, $scope)"
}

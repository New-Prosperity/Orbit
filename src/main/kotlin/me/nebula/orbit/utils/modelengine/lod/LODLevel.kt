package me.nebula.orbit.utils.modelengine.lod

data class LODLevel(
    val maxDistance: Double,
    val visibleBones: Set<String>? = null,
    val hiddenBones: Set<String>? = null,
    val tickRate: Int = 1,
) : Comparable<LODLevel> {
    override fun compareTo(other: LODLevel): Int = maxDistance.compareTo(other.maxDistance)
}

class LODConfigBuilder @PublishedApi internal constructor() {

    @PublishedApi internal val levels = mutableListOf<LODLevel>()
    @PublishedApi internal var cullDistance: Double = 64.0

    fun level(maxDistance: Double, tickRate: Int = 1, block: LODLevelBuilder.() -> Unit = {}) {
        levels += LODLevelBuilder(maxDistance, tickRate).apply(block).build()
    }

    fun cullDistance(distance: Double) { cullDistance = distance }

    @PublishedApi internal fun build(): LODConfig = LODConfig(levels.sorted(), cullDistance)
}

class LODLevelBuilder @PublishedApi internal constructor(
    private val maxDistance: Double,
    private val tickRate: Int,
) {
    @PublishedApi internal var visibleBones: MutableSet<String>? = null
    @PublishedApi internal var hiddenBones: MutableSet<String>? = null

    fun showOnly(vararg bones: String) { visibleBones = bones.toMutableSet() }
    fun hide(vararg bones: String) { hiddenBones = bones.toMutableSet() }

    @PublishedApi internal fun build(): LODLevel = LODLevel(maxDistance, visibleBones, hiddenBones, tickRate)
}

data class LODConfig(
    val levels: List<LODLevel>,
    val cullDistance: Double,
)

inline fun lodConfig(block: LODConfigBuilder.() -> Unit): LODConfig =
    LODConfigBuilder().apply(block).build()

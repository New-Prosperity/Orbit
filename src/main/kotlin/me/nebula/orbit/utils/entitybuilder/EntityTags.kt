package me.nebula.orbit.utils.entitybuilder

import net.minestom.server.entity.Entity
import net.minestom.server.tag.Tag

private val TAGS_TAG: Tag<String> = Tag.String("nebula:tags")

fun Entity.smartTags(): Set<String> =
    getTag(TAGS_TAG)?.split(',')?.filterNot { it.isEmpty() }?.toSet() ?: emptySet()

fun Entity.hasSmartTag(tag: String): Boolean = smartTags().contains(tag)

fun Entity.addSmartTag(tag: String) {
    if (tag.isBlank() || ',' in tag) return
    val current = smartTags()
    if (tag in current) return
    setTag(TAGS_TAG, (current + tag).joinToString(","))
}

fun Entity.removeSmartTag(tag: String) {
    val current = smartTags()
    if (tag !in current) return
    val next = current - tag
    if (next.isEmpty()) {
        removeTag(TAGS_TAG)
    } else {
        setTag(TAGS_TAG, next.joinToString(","))
    }
}

fun Entity.setSmartTags(tags: Iterable<String>) {
    val cleaned = tags.filterNot { it.isBlank() || ',' in it }.toSet()
    if (cleaned.isEmpty()) {
        removeTag(TAGS_TAG)
    } else {
        setTag(TAGS_TAG, cleaned.joinToString(","))
    }
}

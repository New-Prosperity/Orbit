package me.nebula.orbit.utils.commandbuilder

import me.nebula.orbit.user.OrbitOnlineUser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CommandExecutionContextShapeTest {

    @Test
    fun `context exposes a user property typed as OrbitOnlineUser`() {
        val property = CommandExecutionContext::user
        assertNotNull(property)
        assertEquals(OrbitOnlineUser::class, property.returnType.classifier)
    }

    @Test
    fun `context exposes typed reply and targetUser helpers`() {
        val targetUser = CommandExecutionContext::class.members.firstOrNull { it.name == "targetUser" }
        val targetUserOrSelf = CommandExecutionContext::class.members.firstOrNull { it.name == "targetUserOrSelf" }
        assertNotNull(targetUser, "targetUser helper missing")
        assertNotNull(targetUserOrSelf, "targetUserOrSelf helper missing")

        val replyOverloads = CommandExecutionContext::class.members.filter { it.name == "reply" }
        assert(replyOverloads.size >= 2) { "expected both String and TranslationKey reply overloads" }
    }
}

package me.nebula.orbit.user

import me.nebula.gravity.notification.SoundCapable
import me.nebula.gravity.user.Kickable
import me.nebula.gravity.user.NebulaUser
import me.nebula.gravity.user.ServerSwitchable
import me.nebula.orbit.notification.ActionBarCapable
import me.nebula.orbit.notification.TitleCapable
import me.nebula.orbit.notification.ToastCapable
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class OrbitOnlineUserShapeTest {

    @Test
    fun `OrbitOnlineUser implements platform-agnostic capabilities`() {
        val cls = OrbitOnlineUser::class.java
        assertTrue(NebulaUser.Online::class.java.isAssignableFrom(cls))
        assertTrue(Kickable::class.java.isAssignableFrom(cls))
        assertTrue(ServerSwitchable::class.java.isAssignableFrom(cls))
    }

    @Test
    fun `OrbitOnlineUser implements Orbit-local capabilities`() {
        val cls = OrbitOnlineUser::class.java
        assertTrue(Locatable::class.java.isAssignableFrom(cls))
        assertTrue(GuiCapable::class.java.isAssignableFrom(cls))
    }

    @Test
    fun `OrbitOnlineUser implements notification capabilities for all Minecraft surfaces`() {
        val cls = OrbitOnlineUser::class.java
        assertTrue(TitleCapable::class.java.isAssignableFrom(cls))
        assertTrue(ActionBarCapable::class.java.isAssignableFrom(cls))
        assertTrue(ToastCapable::class.java.isAssignableFrom(cls))
        assertTrue(SoundCapable::class.java.isAssignableFrom(cls))
    }
}

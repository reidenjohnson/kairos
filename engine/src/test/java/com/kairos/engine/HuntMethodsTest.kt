package com.kairos.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuntMethodsTest {

    @After
    fun reset() { MethodFilter.enabledMethods = null }

    @Test
    fun `window labels classify to methods`() {
        assertEquals(HuntMethod.EXPANDED_ARCHERY, methodOf("Expanded archery"))
        assertEquals(HuntMethod.ARCHERY, methodOf("Regular archery"))
        assertEquals(HuntMethod.FIREARMS, methodOf("Firearms"))
        assertEquals(HuntMethod.MUZZLELOADER, methodOf("Muzzleloader (statewide)"))
        assertEquals(HuntMethod.GENERAL, methodOf("Youth deer day"))
    }

    @Test
    fun `general windows always show even when filtering weapon methods`() {
        MethodFilter.enabledMethods = setOf(HuntMethod.FIREARMS)
        val deer = seasonsFor("Whitetail deer")!!
        val kept = MethodFilter.windows(deer).map { methodOf(it.label) }.toSet()
        assertTrue(HuntMethod.GENERAL in kept) // youth/residents days stay
        assertTrue(HuntMethod.FIREARMS in kept)
        assertFalse(HuntMethod.ARCHERY in kept)
    }

    @Test
    fun `no filter means everything is on`() {
        MethodFilter.enabledMethods = null
        assertTrue(HuntMethod.entries.all { MethodFilter.isEnabled(it) })
        val deer = seasonsFor("Whitetail deer")!!
        assertEquals(deer.windows.size, MethodFilter.windows(deer).size)
    }

    @Test
    fun `filter drops unsubscribed weapon methods`() {
        MethodFilter.enabledMethods = setOf(HuntMethod.FIREARMS)
        val deer = seasonsFor("Whitetail deer")!!
        val methods = MethodFilter.windows(deer).map { methodOf(it.label) }.toSet()
        assertTrue(HuntMethod.FIREARMS in methods)
        assertFalse(HuntMethod.ARCHERY in methods)
        assertFalse(MethodFilter.isEnabled(HuntMethod.ARCHERY))
    }
}

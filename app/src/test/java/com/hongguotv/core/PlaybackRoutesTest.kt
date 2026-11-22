package com.hongguotv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRoutesTest {
    @Test fun rotatesPlaybackShapesAndRemembersSuccess() {
        val routes = PlaybackRoutes()
        val initial = routes.plan(100, false)
        assertTrue(initial[0].get)
        assertTrue(initial[1].player)
        assertTrue(!initial[2].get && !initial[2].player)
        routes.failed(initial[0], 100, true)
        routes.success(initial[1], 101)
        assertEquals(initial[1], routes.plan(102, false).first())
        routes.failed(initial[1], 102, true)
        assertEquals(initial[2], routes.plan(103, false).first())
    }
}

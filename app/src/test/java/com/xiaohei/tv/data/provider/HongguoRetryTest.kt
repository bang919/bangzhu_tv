package com.xiaohei.tv.data.provider

import com.hongguotv.core.EmptyPlaybackResponseException
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HongguoRetryTest {
    @Test fun recoversAfterEmptyResponses() = runTest {
        var calls = 0
        val result = retryEmptyPlayback {
            calls++
            if (calls < 4) throw EmptyPlaybackResponseException()
            "ready"
        }
        assertEquals("ready", result)
        assertEquals(4, calls)
        assertEquals(8_500L, testScheduler.currentTime)
    }

    @Test fun stopsAfterThreeRetries() = runTest {
        var calls = 0
        try {
            retryEmptyPlayback<Unit> { calls++; throw EmptyPlaybackResponseException() }
            throw AssertionError("Expected empty response")
        } catch (_: EmptyPlaybackResponseException) {
            assertEquals(4, calls)
        }
    }

    @Test fun doesNotRetryOtherErrors() = runTest {
        var calls = 0
        try {
            retryEmptyPlayback<Unit> { calls++; throw IOException("bad response") }
            throw AssertionError("Expected IOException")
        } catch (_: IOException) {
            assertEquals(1, calls)
        }
    }
}

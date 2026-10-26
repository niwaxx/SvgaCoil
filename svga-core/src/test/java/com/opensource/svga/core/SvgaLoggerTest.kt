package com.opensource.svga.core

import com.opensource.svga.core.util.SvgaLogDelegate
import com.opensource.svga.core.util.SvgaLogLevel
import com.opensource.svga.core.util.SvgaLogger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SvgaLoggerTest {

    @Before
    fun setUp() {
        SvgaLogger.isEnabled = true
        SvgaLogger.delegate = null
        SvgaLogger.minLevel = SvgaLogLevel.NONE
    }

    @After
    fun tearDown() {
        SvgaLogger.isEnabled = true
        SvgaLogger.delegate = null
        SvgaLogger.minLevel = SvgaLogLevel.NONE
    }

    @Test
    fun `test default release mode does not evaluate message lambda`() {
        // 模拟 Release 默认环境：minLevel 设为 NONE
        SvgaLogger.minLevel = SvgaLogLevel.NONE

        var lambdaExecuted = false
        SvgaLogger.debug {
            lambdaExecuted = true
            "This debug message should never be constructed"
        }
        SvgaLogger.info {
            lambdaExecuted = true
            "This info message should never be constructed"
        }
        SvgaLogger.warn {
            lambdaExecuted = true
            "This warn message should never be constructed"
        }
        SvgaLogger.error {
            lambdaExecuted = true
            "This error message should never be constructed"
        }

        assertFalse("In Release mode, message lambda must not be evaluated", lambdaExecuted)
    }

    @Test
    fun `test log level filtering with custom delegate`() {
        val capturedLogs = mutableListOf<Triple<Int, String, String>>()
        SvgaLogger.delegate = SvgaLogDelegate { priority, tag, message, _ ->
            capturedLogs.add(Triple(priority, tag, message))
        }

        // 设置为 WARN 级别，只有 WARN 和 ERROR 能通过
        SvgaLogger.minLevel = SvgaLogLevel.WARN

        SvgaLogger.debug { "Debug log" }
        SvgaLogger.info { "Info log" }
        SvgaLogger.warn { "Warn log" }
        SvgaLogger.error { "Error log" }

        assertEquals(2, capturedLogs.size)
        assertEquals("Warn log", capturedLogs[0].third)
        assertEquals("Error log", capturedLogs[1].third)
    }

    @Test
    fun `test global enable switch completely disables logging`() {
        val capturedLogs = mutableListOf<String>()
        SvgaLogger.delegate = SvgaLogDelegate { _, _, message, _ ->
            capturedLogs.add(message)
        }

        SvgaLogger.minLevel = SvgaLogLevel.VERBOSE
        SvgaLogger.isEnabled = false

        var lambdaEvaluated = false
        SvgaLogger.error {
            lambdaEvaluated = true
            "Error log"
        }

        assertFalse(lambdaEvaluated)
        assertTrue(capturedLogs.isEmpty())
    }

    @Test
    fun `test explicit debug mode logs all debug messages`() {
        val capturedLogs = mutableListOf<String>()
        SvgaLogger.delegate = SvgaLogDelegate { _, _, message, _ ->
            capturedLogs.add(message)
        }

        SvgaLogger.minLevel = SvgaLogLevel.DEBUG

        SvgaLogger.debug { "Debug log 1" }
        SvgaLogger.info { "Info log 2" }

        assertEquals(2, capturedLogs.size)
        assertEquals("Debug log 1", capturedLogs[0])
        assertEquals("Info log 2", capturedLogs[1])
    }

    @Test
    fun `test init helper function`() {
        SvgaLogger.init(isDebug = false)
        assertEquals(SvgaLogLevel.NONE, SvgaLogger.minLevel)

        SvgaLogger.init(isDebug = true)
        assertEquals(SvgaLogLevel.DEBUG, SvgaLogger.minLevel)
    }
}

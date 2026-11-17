package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests the real frame layout with size messages; UIKit interop needs an app-hosted test runner. */
@OptIn(ExperimentalTestApi::class)
class LegacyUiFrameViewportIosTest {

    private var height by mutableStateOf<Int?>(null)
    private var width by mutableStateOf<Int?>(null)
    private val host = LegacyUiFrameHost(onHeight = { height = it }, onWidth = { width = it }, onMessage = { _, _ -> })

    @Test
    fun longWideResourceGrowsWithTheThreadAndScrollsOnlyHorizontally() = runComposeUiTest {
        setContent {
            LibreChatTheme {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    LegacyUiFrameViewport(320.dp, 80.dp, height, width, Modifier.testTag(FRAME)) {
                        Box(it.testTag(CONTENT))
                    }
                    Text("After widget")
                }
            }
        }
        runOnIdle {
            host.onFrameMessage("""{"type":"ui-size-change","payload":{"height":2400,"width":960}}""")
        }
        waitForIdle()
        val frame = onNodeWithTag(FRAME).getUnclippedBoundsInRoot()
        assertEquals(2400.dp, frame.height)
        assertEquals(320.dp, frame.width)
        assertEquals(960.dp, onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().width)

        val horizontal = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
        val range = horizontal.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue(range.maxValue() > 0f)
        horizontal.performSemanticsAction(SemanticsActions.ScrollBy) { it(600f, 0f) }
        waitForIdle()
        assertTrue(range.value() > 0f)
        onNodeWithText("After widget").performScrollTo().assertIsDisplayed()

        runOnIdle {
            host.onFrameMessage("""{"type":"ui-size-change","payload":{"height":300,"width":320}}""")
        }
        waitForIdle()
        assertEquals(300.dp, onNodeWithTag(FRAME).getUnclippedBoundsInRoot().height)
        assertEquals(0f, range.maxValue())
    }

    @Test
    fun heightOnlyReportsKeepTheResourceAtTheChatWidth() = runComposeUiTest {
        setContent {
            LibreChatTheme {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    LegacyUiFrameViewport(320.dp, 80.dp, height, width, Modifier.testTag(FRAME)) {
                        Box(it.testTag(CONTENT))
                    }
                }
            }
        }
        assertEquals(240.dp, onNodeWithTag(FRAME).getUnclippedBoundsInRoot().height)
        runOnIdle {
            host.onFrameMessage("""{"type":"ui-size-change","payload":{"height":1800}}""")
        }
        waitForIdle()
        assertEquals(1800.dp, onNodeWithTag(FRAME).getUnclippedBoundsInRoot().height)
        assertEquals(320.dp, onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().width)
        val horizontal = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
        assertEquals(0f, horizontal.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].maxValue())
    }

    private companion object {
        const val FRAME = "legacy-frame"
        const val CONTENT = "webview-layout"
    }
}

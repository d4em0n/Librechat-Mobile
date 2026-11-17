package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
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
import com.garfiec.librechat.core.model.mcp.UiResource
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Uses the real WKWebView and sandbox relay rather than calling the resize handler directly. */
@OptIn(ExperimentalTestApi::class)
class LegacyUiResourceBlockIosTest {

    @Test
    fun longWideResourceGrowsWithTheThreadAndScrollsOnlyHorizontally() = runComposeUiTest {
        var answer: ((Boolean) -> Unit)? = null
        val resource = resource(
            """
            <style>html,body{margin:0;overflow:hidden}table{width:960px;height:2400px}</style>
            <table><tr><td>First column</td><td>Last column</td></tr></table>
            <script>
            parent.postMessage({type:'ui-size-change',payload:{height:2400,width:960}},'*');
            parent.postMessage({type:'prompt',messageId:'resize',payload:{prompt:'Resize'}},'*');
            addEventListener('message',function(event){
              if(event.source===parent && event.data.type==='ui-message-response'){
                document.querySelector('table').style.cssText='width:320px;height:300px';
                parent.postMessage({type:'ui-size-change',payload:{height:300,width:320}},'*');
              }
            });
            </script>
            """.trimIndent(),
        )
        setContent {
            LibreChatTheme {
                CompositionLocalProvider(
                    LocalLegacyUiResources provides LegacyUiResources(
                        enabled = true,
                        byId = mapOf(ID to resource),
                        onMessage = { _, reply -> answer = reply },
                    ),
                ) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        LegacyUiResourceBlock(listOf(ID), streaming = false, modifier = Modifier.testTag(FRAME))
                        Text("After widget")
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 15_000) {
            onNodeWithTag(FRAME).getUnclippedBoundsInRoot().height >= 2400.dp && answer != null
        }
        val frame = onNodeWithTag(FRAME).getUnclippedBoundsInRoot()
        assertEquals(2400.dp, frame.height)
        assertEquals(320.dp, frame.width)

        val horizontal = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
        val range = horizontal.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue(range.maxValue() > 0f)
        horizontal.performSemanticsAction(SemanticsActions.ScrollBy) { it(600f, 0f) }
        waitForIdle()
        assertTrue(range.value() > 0f)
        onNodeWithText("After widget").performScrollTo().assertIsDisplayed()

        runOnIdle { answer!!(true) }
        waitUntil(timeoutMillis = 15_000) {
            onNodeWithTag(FRAME).getUnclippedBoundsInRoot().height == 300.dp
        }
        assertEquals(0f, range.maxValue())
    }

    @Test
    fun heightOnlyReportsKeepTheResourceAtTheChatWidth() = runComposeUiTest {
        val resource = resource(
            """
            <script>parent.postMessage({type:'ui-size-change',payload:{height:1800}},'*');</script>
            """.trimIndent(),
        )
        setContent {
            LibreChatTheme {
                CompositionLocalProvider(
                    LocalLegacyUiResources provides LegacyUiResources(true, mapOf(ID to resource)) { _, _ -> },
                ) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        LegacyUiResourceBlock(listOf(ID), streaming = false, modifier = Modifier.testTag(FRAME))
                    }
                }
            }
        }
        waitUntil(timeoutMillis = 15_000) {
            onNodeWithTag(FRAME).getUnclippedBoundsInRoot().height == 1800.dp
        }
        assertEquals(320.dp, onNodeWithTag(FRAME).getUnclippedBoundsInRoot().width)
        val horizontal = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
        assertEquals(0f, horizontal.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].maxValue())
    }

    private fun resource(html: String): UiResource = requireNotNull(
        UiResource.from(buildJsonObject {
            put("resourceId", ID)
            put("uri", "ui://test/layout.html")
            put("mimeType", "text/html")
            put("text", html)
        }),
    )

    private companion object {
        const val ID = "layout-test"
        const val FRAME = "legacy-frame"
    }
}

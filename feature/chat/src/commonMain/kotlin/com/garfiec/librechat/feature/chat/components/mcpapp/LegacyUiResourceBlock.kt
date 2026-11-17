package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.mcp.UiResource
import com.garfiec.librechat.core.model.mcp.isLegacyHtml
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.mcp_app_loading_interactive_view
import com.garfiec.librechat.feature.chat.resources.ui_resource_not_found
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** Cached per core/ui Compose perf rule 13. */
private val FRAME_SHAPE = RoundedCornerShape(12.dp)

/** Before the resource reports a size; web's iframe has none either, so it is a guess. */
private val DEFAULT_HEIGHT = 240.dp

/** Keep a usable placeholder until the resource reports its content height. */
private val MIN_HEIGHT = 80.dp

/** Upstream `UIResourceCarousel`'s card size. */
private val CAROUSEL_CARD_WIDTH = 230.dp
private val CAROUSEL_CARD_MIN_HEIGHT = 360.dp

/**
 * What one `\ui{…}` marker draws: nothing when `legacyHtmlEnabled` is off or no named resource is a
 * legacy HTML one, the one resource for a single id, else a horizontal carousel (upstream
 * `MCPUIResource` / `MCPUIResourceCarousel`). A single id the conversation doesn't hold shows web's
 * "UI Resource not found" chip, whatever `legacyHtmlEnabled` says (web checks it first too); a
 * carousel drops unknown ids silently. While the reply streams it holds a placeholder: a web view
 * there would reload on every rebuild of the streaming item, and again when the reply is saved.
 */
@Composable
internal fun LegacyUiResourceBlock(ids: List<String>, streaming: Boolean, modifier: Modifier = Modifier) {
    val local = LocalLegacyUiResources.current ?: return
    val single = ids.singleOrNull()
    if (single != null && single !in local.byId) {
        // Not while streaming: the resource's attachment can still arrive after its marker.
        if (!streaming) UiResourceNotFoundChip(single, modifier)
        return
    }
    if (!local.enabled) return
    val resources = remember(ids, local.byId) {
        ids.mapNotNull { id -> local.byId[id]?.takeIf { it.isLegacyHtml && it.inlineHtml() != null } }
    }
    if (resources.isEmpty()) return
    if (streaming) {
        Box(
            modifier = modifier
                .height(DEFAULT_HEIGHT)
                .clip(FRAME_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.mcp_app_loading_interactive_view),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val onMessage by rememberUpdatedState(local.onMessage)
    BoxWithConstraints(modifier = modifier) {
        val single = resources.singleOrNull()
        if (single != null) {
            key(single.key) {
                LegacyUiFrame(single, width = maxWidth, minHeight = MIN_HEIGHT, onMessage = onMessage)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                resources.forEachIndexed { index, resource ->
                    key(index, resource.key) {
                        LegacyUiFrame(
                            resource,
                            width = CAROUSEL_CARD_WIDTH,
                            minHeight = CAROUSEL_CARD_MIN_HEIGHT,
                            onMessage = onMessage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyUiFrame(
    resource: UiResource,
    width: Dp,
    minHeight: Dp,
    onMessage: (String, (Boolean) -> Unit) -> Unit,
) {
    val html = remember(resource) { resource.inlineHtml().orEmpty() }
    // The platform views load their page once, when created: a resource whose content changed is a
    // new page, so it gets a new view, a new host (the old view's release clears only its own) and
    // its own height.
    key(html) {
        var reported by remember { mutableStateOf<Int?>(null) }
        var reportedWidth by remember { mutableStateOf<Int?>(null) }
        val host = remember {
            LegacyUiFrameHost(
                onHeight = { reported = it },
                onMessage = { text, reply -> onMessage(text, reply) },
                onWidth = { reportedWidth = it },
            )
        }
        LegacyUiFrameViewport(width, minHeight, reported, reportedWidth) { frameModifier ->
            LegacyUiResourceWebView(
                page = remember { LegacyUiRelayPage.build(html) },
                host = host,
                modifier = frameModifier,
            )
        }
    }
}

/** The thread owns vertical scrolling; only wider-than-pane resources scroll inside a card. */
@Composable
internal fun LegacyUiFrameViewport(
    width: Dp,
    minHeight: Dp,
    reportedHeight: Int?,
    reportedWidth: Int?,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier
            .width(width)
            .clip(FRAME_SHAPE)
            .horizontalScroll(rememberScrollState()),
    ) {
        content(
            Modifier
                .width((reportedWidth?.dp ?: width).coerceAtLeast(width))
                .height((reportedHeight?.dp ?: DEFAULT_HEIGHT).coerceAtLeast(minHeight)),
        )
    }
}

/** Web's chip is `rounded` (4px). */
private val CHIP_SHAPE = RoundedCornerShape(4.dp)

/**
 * How long an unknown id waits before its chip shows: the conversation's resources are rebuilt off
 * the main thread, a moment after the messages that name them, so a chat opening would otherwise
 * flash the chip over a resource that is about to arrive.
 */
private const val NOT_FOUND_GRACE_MS = 300L

/** Upstream `MCPUIResource`'s chip for an id the conversation doesn't hold. */
@Composable
private fun UiResourceNotFoundChip(id: String, modifier: Modifier = Modifier) {
    var shown by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        delay(NOT_FOUND_GRACE_MS)
        shown = true
    }
    if (!shown) return
    Text(
        text = stringResource(Res.string.ui_resource_not_found, id),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CHIP_SHAPE)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

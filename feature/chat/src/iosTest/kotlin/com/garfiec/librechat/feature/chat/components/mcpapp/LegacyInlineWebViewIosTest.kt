package com.garfiec.librechat.feature.chat.components.mcpapp

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIView
import platform.WebKit.WKWebViewConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Exercises the native WebView without the headless Compose runner's unsupported UIKitView host. */
@OptIn(ExperimentalForeignApi::class)
class LegacyInlineWebViewIosTest {

    @Test
    fun movingTheCardThroughAClippedParentDoesNotInsetItsContent() {
        val parent = object : UIView(frame = CGRectMake(0.0, 0.0, 320.0, 600.0)) {
            override fun safeAreaInsets() = UIEdgeInsetsMake(64.0, 0.0, 34.0, 0.0)
        }
        parent.clipsToBounds = true
        val webView = LegacyInlineWebView(WKWebViewConfiguration())
        parent.addSubview(webView)
        for (top in listOf(100.0, 0.0, -350.0, -100.0, 100.0)) {
            webView.setFrame(CGRectMake(0.0, top, 320.0, 700.0))
            parent.layoutIfNeeded()
            webView.safeAreaInsets.useContents {
                assertEquals(0.0, this.top)
                assertEquals(0.0, bottom)
            }
            webView.scrollView.adjustedContentInset.useContents {
                assertEquals(0.0, this.top)
                assertEquals(0.0, bottom)
            }
            assertFalse(webView.scrollView.scrollEnabled)
        }
        webView.removeFromSuperview()
    }
}

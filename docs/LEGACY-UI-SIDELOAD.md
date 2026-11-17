# Legacy MCP-UI test build

This fork lets legacy `text/html` widgets grow to their reported content height in
the conversation. The conversation owns vertical scrolling. A resource reporting
a width larger than the chat pane can scroll horizontally inside its card;
height-only reports retain the pane width. Later size reports update both axes.
The sandbox and iOS WebView navigation/message policies remain unchanged.

Push `fix/legacy-mcp-ui-natural-size` to run **Legacy MCP-UI SideStore IPA**.
It checks the legacy host messages and chat lint, runs frame layout/size-message
regressions on the iOS simulator, and uses upstream's unsigned Release archive
recipe to package a device ARM64 IPA. No Apple signing credentials are required.

Download the `switchboard-legacy-ui-unsigned-ios` artifact from the successful
GitHub Actions run, extract `switchboard-legacy-ui.ipa`, and import it in SideStore
for signing/installing. `source-commit.txt` identifies the source and the adjacent
SHA-256 file verifies the IPA. Compose's headless test runner cannot host UIKit
interop views, so the layout tests use a sized box in place of the WebView.
WebView rendering and physical iPhone gesture acceptance remain device checks.

The job allows two hours for a cold build (the archive step allows 90 minutes).
The simulator and test Gradle daemon are stopped before the device Release link
to free runner memory. Gradle caches are writable on this fork's test branch.

Check a long itinerary all the way to its last item, a wide table sideways, a
responsive calculator, and map pan/zoom while ordinary thread scrolling still
works. Also check reopening the conversation and portrait/landscape rotation.

This changes Switchboard's legacy renderer only. It does not modify LibreChat,
the Frontier renderer or either Frontier MCP wrapper.

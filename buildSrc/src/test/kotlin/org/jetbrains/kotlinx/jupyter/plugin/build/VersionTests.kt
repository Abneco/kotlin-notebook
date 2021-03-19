package org.jetbrains.kotlinx.jupyter.plugin.build

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class VersionTests {

    @Test
    fun `version is parsed correctly`() {
        assertEquals(
            PluginVersion("0.0.1-Dev.42", "0.0.1", "Dev", "42"),
            PluginVersion.fromVersion("0.0.1-Dev.42")
        )

        assertEquals(
            PluginVersion("0.0.1.42", "0.0.1", DEFAULT_CHANNEL_NAME, "42"),
            PluginVersion.fromVersion("0.0.1.42")
        )
    }
}

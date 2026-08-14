package com.jbsan.ldapadvisor.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlEscaperTest {
    @Test
    fun escapesMarkup() {
        assertEquals(
            "&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;&amp;&#39;",
            HtmlEscaper.escape("<script>alert(\"x\")</script>&'"),
        )
    }
}

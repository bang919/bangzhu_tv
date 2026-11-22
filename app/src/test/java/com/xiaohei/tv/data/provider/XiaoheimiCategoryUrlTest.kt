package com.xiaohei.tv.data.provider

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class XiaoheimiCategoryUrlTest {
    @Test fun languageFilterPreservesOtherFiltersAndPage() {
        val url = categoryRequestUrl("https://xiaoheimi.cc/index.php/vod/show/area/%E9%A6%99%E6%B8%AF/by/time/id/1/lang/%E7%B2%A4%E8%AF%AD/page/2.html").toHttpUrl()
        assertEquals("/index.php/vod/show.html", url.encodedPath)
        assertEquals("粤语", url.queryParameter("lang"))
        assertEquals("香港", url.queryParameter("area"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("1", url.queryParameter("id"))
        assertEquals("time", url.queryParameter("by"))
    }

    @Test fun unrelatedAndUnexpectedPathsAreUnchanged() {
        val urls = listOf("https://xiaoheimi.cc/index.php/vod/show/id/1.html", "https://xiaoheimi.cc/index.php/vod/show/id/1/lang/粤语/unknown/x.html")
        urls.forEach { assertEquals(it, categoryRequestUrl(it)) }
    }
}

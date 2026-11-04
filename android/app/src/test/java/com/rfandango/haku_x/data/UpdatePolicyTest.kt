package com.rfandango.haku_x.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
  private val installed = "1.3.0-advanced5.19-upstream"
  private fun release(v: String = "5.19.1", preview: Boolean = false): JSONObject {
    val tag = "advanced-1.3.0-$v" + if (preview) "-preview.1" else ""
    val name = "HakuX-Advanced-1.3.0-$v-updater-arm64.apk"
    val apk = JSONObject().put("name", name).put("state", "uploaded").put("size", 12345)
      .put("digest", "sha256:" + "a".repeat(64))
      .put("browser_download_url", "https://github.com/JDewitz/HakuX-Advanced/releases/download/$tag/$name")
    return JSONObject().put("tag_name", tag).put("name", "Advanced $v")
      .put("draft", false).put("prerelease", preview).put("assets", JSONArray().put(apk))
  }
  private fun select(vararg rs: JSONObject, previews: Boolean = true) =
    UpdatePolicy.select(JSONArray(rs.toList()), installed, previews)

  @Test fun hotfixIsNewerAndAssetIsMatched() {
    val found = select(release())!!
    assertEquals("1.3.0-advanced5.19.1", found.version)
    assertEquals("a".repeat(64), found.sha256)
  }
  @Test fun numericOrderBeatsPublicationOrderAndThemeReleases() {
    val theme = release().put("tag_name", "themes-nostalgia-v9")
    assertEquals("1.3.0-advanced5.20", select(theme, release("5.20"), release("5.9"), release())!!.version)
    assertTrue(UpdatePolicy.installedVersion("1.3.0-advanced5.10-hud")!! >
      UpdatePolicy.installedVersion("1.3.0-advanced5.9-compat")!!)
  }
  @Test fun officialMalformedAndSameVersionAreIgnored() {
    assertNull(select(release().put("tag_name", "v1.4.0")))
    assertNull(select(release("5.19")))
    assertNull(select(release().put("tag_name", "advanced-1.3.0-999999999999.1")))
    assertNull(UpdatePolicy.select(JSONArray().put(release()), "unknown", true))
  }
  @Test fun previewChannelAndStablePromotion() {
    assertNull(select(release(preview = true), previews = false))
    assertNotNull(select(release(preview = true)))
    assertNull(select(release().put("prerelease", true), previews = false))
    val current = "1.3.0-advanced5.19.1-preview.1"
    assertNotNull(UpdatePolicy.select(JSONArray().put(release()), current, false))
  }
  @Test fun draftAndMissingApkAreIgnored() {
    assertNull(select(release().put("draft", true)))
    assertNull(select(release().put("assets", JSONArray())))
  }
  @Test fun wrongRepositoryAndNonHttpsAssetsAreRejected() {
    for (url in listOf("http://github.com/file.apk", "https://evil.example/file.apk",
      "https://github.com/rfandango/hakuX/releases/download/v1.4/app.apk")) {
      val r = release(); r.getJSONArray("assets").getJSONObject(0).put("browser_download_url", url)
      assertNull(select(r))
    }
  }
  @Test fun wrongArchitectureVersionAndAmbiguousApksAreRejected() {
    for (name in listOf("official.apk", "HakuX-Advanced-1.3.0-5.19.1-x86.apk", "HakuX-Advanced-1.3.0-5.20-arm64.apk")) {
      val r = release(); r.getJSONArray("assets").getJSONObject(0).put("name", name)
      assertNull(select(r))
    }
    val r = release(); val a = r.getJSONArray("assets"); a.put(a.getJSONObject(0)); assertNull(select(r))
  }
  @Test fun digestSizeAndUploadStateAreRequired() {
    for ((key, value) in listOf("digest" to "", "size" to 0, "size" to 200_000_000, "state" to "new")) {
      val r = release(); r.getJSONArray("assets").getJSONObject(0).put(key, value)
      assertNull(select(r))
    }
  }
  @Test fun malformedReleaseDoesNotHideValidUpdate() {
    assertNotNull(select(JSONObject(), release()))
  }
}

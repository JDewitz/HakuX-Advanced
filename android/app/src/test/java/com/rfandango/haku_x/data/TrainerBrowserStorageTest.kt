package com.rfandango.haku_x.data

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class TrainerBrowserStorageTest {
  private fun fixture(marker: Int=0, title: Long=0x12345678L): ByteArray = ByteArray(96).also { d ->
    fun put(p: Int,v: Long) { repeat(4) {d[p+it]=(v shr(it*8)).toByte()} }
    listOf(0 to 96L,6 to 35L,10 to 44L,14 to 45L,18 to 36L,22 to 35L,36 to title,
      45 to 61L,49 to 66L,53 to 71L).forEach {(p,v)->put(p,v)}
    d[4]=1;d[35]=0xc3.toByte();"Name\u0000Note\u0000Option\u0000".toByteArray().copyInto(d,61);d[90]=marker.toByte()
  }
  private fun temp(block:(File)->Unit) {
    val root=Files.createTempDirectory("hakux-trainers").toFile()
    try {block(root)} finally {root.deleteRecursively()}
  }
  @Test fun switchingAlternativesRetainsOptionsAndOnlyOneActiveBinary() = temp { root ->
    val store=TrainerStore(root,"12345678.1")
    var a=store.select(fixture(1));a=store.option(a,0,true);a=store.enable(a,true)
    val b=store.select(fixture(2));assertFalse(b.enabled);assertEquals("0",b.options)
    assertEquals(b.file,store.load()!!.file)
    val restored=TrainerStore(root,"12345678.1").select(fixture(1))
    assertTrue(restored.enabled);assertEquals("1",restored.options);assertEquals(a.file,restored.file)
  }
  @Test fun gameRevisionsHaveIndependentSelections() = temp { root ->
    val a=TrainerStore(root,"12345678.1");val b=TrainerStore(root,"12345678.2")
    val selected=a.select(fixture());a.enable(selected,true)
    assertNull(b.load());assertFalse(b.select(fixture()).enabled)
    assertTrue(a.load()!!.enabled)
  }
  @Test fun mismatchedTitleCannotReplaceExistingSelection() = temp { root ->
    val store=TrainerStore(root,"12345678.1");val current=store.select(fixture())
    try {store.select(fixture(title=0x87654321));fail("Accepted wrong title")} catch(_:IllegalArgumentException){}
    assertEquals(current,store.load())
  }
  @Test fun legacyConfigMigratesWithoutLosingEnabledOptions() = temp { root ->
    val store=TrainerStore(root,"12345678.1");var a=store.select(fixture(1));a=store.option(a,0,true);store.enable(a,true)
    val config=File(a.file.parentFile,"choices.json");val legacy=JSONObject(config.readText());legacy.remove("history");config.writeText(legacy.toString())
    assertTrue(store.load()!!.enabled)
    store.select(fixture(2));val restored=store.select(fixture(1))
    assertTrue(restored.enabled);assertEquals("1",restored.options)
  }
  @Test fun clearingSelectionDisablesFutureLaunchButRetainsHistory() = temp { root ->
    val store=TrainerStore(root,"12345678.1");val a=store.option(store.select(fixture()),0,true);store.enable(a,true)
    store.remove();assertNull(store.load())
    val restored=store.select(fixture());assertFalse(restored.enabled);assertEquals("1",restored.options)
  }
  @Test fun folderDuplicatesMergeAndSurviveCatalogRestart() = temp { root ->
    val c=TrainerCatalog(root);val a=c.cache(fixture(),"folder/One.etm");val b=c.cache(fixture(),"other/TWO.ETM")
    c.save(listOf(a,b));val read=TrainerCatalog(root).read()
    assertEquals(1,read.size);assertEquals(listOf("folder/One.etm","other/TWO.ETM"),read.single().sources)
    assertEquals(a.trainer,read.single().trainer)
  }
  @Test fun sameNamesWithDifferentBinariesRemainDistinct() = temp { root ->
    val c=TrainerCatalog(root);c.save(listOf(c.cache(fixture(1),"a.etm"),c.cache(fixture(2),"b.etm")))
    assertEquals(2,c.read().size);assertEquals(1,c.read().map{it.trainer.name}.toSet().size)
  }
  @Test fun corruptPayloadIsRejectedOnSelectionWithoutRescanningOtherTrainers() = temp { root ->
    val c=TrainerCatalog(root);val a=c.cache(fixture(1),"a.etm");val b=c.cache(fixture(2),"b.etm");c.save(listOf(a,b))
    a.file.writeBytes(fixture(3));assertEquals(TrainerCatalog.merge(listOf(a,b)),c.read())
    try { c.verifiedBytes(a);fail("Accepted modified executable") } catch (_: IllegalArgumentException) {}
    assertArrayEquals(fixture(2),c.verifiedBytes(b))
  }
  @Test fun selectedBinaryIsIndependentOfCatalogChanges() = temp { root ->
    val c=TrainerCatalog(File(root,"catalog"));val entry=c.cache(fixture(),"source.etm")
    val store=TrainerStore(root,"12345678.1");val selected=store.select(entry.file.readBytes())
    entry.file.writeBytes(fixture(2))
    assertArrayEquals(fixture(),store.load()!!.file.readBytes());assertNotEquals(entry.file,selected.file)
  }
  @Test fun streamsRejectOversizeAndInvalidBinaryDoesNotEnterCatalog() = temp { root ->
    try {ByteArray(Etm.MAX_SIZE+1).inputStream().readTrainerBytes();fail("Accepted oversized trainer")} catch(_:IllegalArgumentException){}
    val c=TrainerCatalog(root)
    try {c.cache(ByteArray(40),"bad.etm");fail("Accepted malformed trainer")} catch(_:IllegalArgumentException){}
    assertTrue(root.listFiles().orEmpty().isEmpty())
  }
  @Test fun largePackRestartAndGameMatchingNeverOpenTrainerPayloads() = temp { root ->
    val c=TrainerCatalog(root)
    val entries=(0 until 2200).map { n ->
      val bytes=fixture(title=0x10000000L+(n%200)).also { d -> repeat(4) { d[88+it]=(n ushr (8*it)).toByte() } }
      c.cache(bytes,"Imported: Trainer$n.etm")
    }
    c.save(entries)
    var reads=0
    val restarted=TrainerCatalog(root) { reads++;error("Browse must not open executable trainers") }
    val indexed=restarted.read();assertEquals(2200,indexed.size)
    val lookup=TrainerLookup(indexed)
    repeat(100) { assertEquals(11,lookup.forGame("10000005.8").size) }
    assertTrue(lookup.forGame("00000000.1").isEmpty());assertTrue(lookup.forGame("not-a-title").isEmpty())
    assertEquals(0,reads)
  }
  @Test fun legacyCatalogIsMigratedOnceAndKeepsSourcesAndMetadata() = temp { root ->
    val c=TrainerCatalog(root);val a=c.cache(fixture(1),"Imported: old.etm");val b=c.cache(fixture(2),"Folder: other.etm")
    val legacy=JSONArray();for(e in listOf(a,b)) legacy.put(JSONObject().put("id",e.id).put("sources",JSONArray(e.sources)))
    File(root,"index.json").writeText(legacy.toString())
    var reads=0
    val first=TrainerCatalog(root) { reads++;it.readBytes() }.read()
    assertEquals(2,reads);assertEquals(TrainerCatalog.merge(listOf(a,b)),first)
    val second=TrainerCatalog(root) { error("Migrated index reopened a trainer") }.read()
    assertEquals(first,second);assertEquals(2,JSONObject(File(root,"index.json").readText()).getInt("version"))
  }
  @Test fun atomicImportMergeAndFolderRefreshPreserveManualEntries() = temp { root ->
    val first=TrainerCatalog(root);val second=TrainerCatalog(root)
    val a=first.cache(fixture(1),"Imported: a.etm");val b=second.cache(fixture(2),"Imported: b.etm")
    first.add(listOf(a));second.add(listOf(b));assertEquals(2,first.read().size)
    val folder=first.cache(fixture(3),"Folder: c.etm")
    assertEquals(3,first.replaceFolder(listOf(folder)).size)
    assertEquals(TrainerCatalog.merge(listOf(a,b)),second.replaceFolder(emptyList()))
  }
  @Test fun missingPayloadDoesNotTriggerPackScanAndFailsOnlyWhenSelected() = temp { root ->
    val c=TrainerCatalog(root);val a=c.cache(fixture(),"Imported: a.etm");c.save(listOf(a));a.file.delete()
    assertEquals(listOf(a),TrainerCatalog(root).read())
    try {c.verifiedBytes(a);fail("Accepted missing payload")} catch (_: java.io.IOException) {}
  }
  @Test fun lookupDeduplicatesMultipleTitleIdsAndKeepsRevisionMatching() = temp { root ->
    val c=TrainerCatalog(root);val a=c.cache(fixture(),"Imported: a.etm")
    val entry=a.copy(trainer=a.trainer.copy(titleIds=listOf(0x12345678L,0x12345678L,0x87654321L)))
    val lookup=TrainerLookup(listOf(entry))
    assertEquals(listOf(entry),lookup.forGame("12345678.1"));assertEquals(listOf(entry),lookup.forGame("12345678.9"))
    assertEquals(listOf(entry),lookup.forGame("87654321.0"))
  }

  @Test fun sha256IdentityStaysCompatibleWithExistingSelections() {
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",trainerHash("abc".toByteArray()))
  }

}

package com.rfandango.haku_x.data

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
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
  @Test fun corruptCachedEntryDoesNotHideHealthyTrainers() = temp { root ->
    val c=TrainerCatalog(root);val a=c.cache(fixture(1),"a.etm");val b=c.cache(fixture(2),"b.etm");c.save(listOf(a,b))
    a.file.writeBytes(fixture(3));assertEquals(listOf(b),c.read())
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
}

package com.rfandango.haku_x.advanced

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OnlinePlayPolicyTest {
  @Test fun legacyMissingOrInvalidValuesDefaultToProtection() {
    assertTrue(OnlinePlayPolicy.fromJson(JSONObject()).onlinePlay)
    for(value in listOf<Any>("false",0,JSONObject.NULL)) assertTrue(OnlinePlayPolicy.fromJson(JSONObject().put("onlinePlay",value)).onlinePlay)
    assertFalse(OnlinePlayPolicy.fromJson(JSONObject().put("onlinePlay",false)).onlinePlay)
  }
  @Test fun onlineSessionsNeverAllowInjectionStateLoadsOrMemoryTools() {
    val p=OnlinePlayPolicy(true)
    assertFalse(p.allowGuestMods);assertFalse(p.allowStateLoad);assertFalse(p.allowMemoryTools)
    for(mode in listOf("off","lan","kai","insignia")) assertEquals(mode,p.networkMode(mode))
  }
  @Test fun trainerSessionsForceAllNetworkingOffIncludingUnknownFutureModes() {
    val p=OnlinePlayPolicy(false)
    assertTrue(p.allowGuestMods);assertTrue(p.allowStateLoad)
    for(mode in listOf("off","lan","kai","insignia","nat","future")) assertEquals("off",p.networkMode(mode))
  }
  @Test fun modesAreMutuallyExclusive() {
    for(online in listOf(true,false)) for(mode in listOf("off","lan","kai","insignia")) {
      val p=OnlinePlayPolicy(online)
      assertFalse(p.allowGuestMods && p.networkMode(mode)!="off")
    }
  }
  @Test fun relaunchPinsParentModeDespiteSettingsChanges() {
    assertFalse(OnlinePlayPolicy(true).propagateFrom(false).onlinePlay)
    assertTrue(OnlinePlayPolicy(false).propagateFrom(true).onlinePlay)
    assertTrue(OnlinePlayPolicy().propagateFrom(null).onlinePlay)
  }
  @Test fun nativeDebugNetworkOverridesCannotReenableTrainerSessionNetworking() {
    val offline=OnlinePlayPolicy(false)
    assertFalse(OnlineLaunchGuard.pin(offline,null,listOf("","")).onlinePlay)
    assertTrue(OnlineLaunchGuard.pin(offline,"net=nat",listOf("","")).onlinePlay)
    assertTrue(OnlineLaunchGuard.pin(offline,null,listOf("NC_RAM_MB=128;NC_NET=net=lan","")).onlinePlay)
    assertTrue(OnlineLaunchGuard.pin(offline,null,listOf(""," NC_NET=net=kai")).onlinePlay)
    assertTrue(OnlineLaunchGuard.pin(offline,null,listOf(null,"")).onlinePlay)
    assertFalse(OnlineLaunchGuard.pin(offline,null,listOf("NC_RAM_MB=128","OTHER_NC_NET=x")).onlinePlay)
  }
}

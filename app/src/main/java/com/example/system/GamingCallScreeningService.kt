package com.example.system

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService

class GamingCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val guardPrefs = FocusGuardPreferences(this)
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            true
        }

        val shouldDecline = isIncoming && guardPrefs.isGamingCallBlockActive()
        val response = if (shouldDecline) {
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSilenceCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()
        } else {
            CallResponse.Builder().build()
        }
        respondToCall(callDetails, response)
    }
}

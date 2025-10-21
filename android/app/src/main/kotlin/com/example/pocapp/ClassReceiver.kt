package com.example.pocapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {
    fun normalizeIndianNumber(rawNumber: String?): String {
        if (rawNumber.isNullOrBlank()) return ""
        // Remove all non-digit characters (spaces, hyphens, +, etc.)
        var number = rawNumber.replace("\\D".toRegex(), "")
        // Remove leading 91 or 0 if present
        number = when {
            number.startsWith("91") && number.length > 10 -> number.substring(number.length - 10)
            number.startsWith("0") && number.length > 10 -> number.substring(number.length - 10)
            number.length > 10 -> number.substring(number.length - 10)
            else -> number
        }
        return number
    }
    override fun onReceive(context: Context?, intent: Intent?) {
        val state = intent?.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent?.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        Log.d("CallReceiver", "State: $state, Number: $number")

        if (state == TelephonyManager.EXTRA_STATE_RINGING && number != null) {
            val serviceIntent = Intent(context, CallService::class.java)
            var processedNumber = normalizeIndianNumber(number)
            Log.d("CallReceiver", "Processed number: $state, processedNumber: $processedNumber")

            serviceIntent.putExtra("incoming_number", processedNumber)
            context?.startForegroundService(serviceIntent)
        }
    }
}
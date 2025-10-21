package com.example.poc2

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.example.poc2.model.ApiResult
import com.example.poc2.model.DialerPopupInfo
import com.example.poc2.services.ApiClient
import com.example.poc2.utils.Constants
import kotlinx.coroutines.*
import kotlin.math.abs

class CallService : Service() {

    companion object {
        val TAG = CallService::class.java.name
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private var loader: ProgressBar? = null
    private var dataCard: LinearLayout? = null
    private var nameView: TextView? = null
    private var statusView: TextView? = null
    private var notesView: TextView? = null
    private var lastActDays: TextView? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var number = intent?.getStringExtra("incoming_number")?:""
//        number = "7358430404"
        Log.d("CallService", "Incoming call from: $number")

        destroyPopup()
        showPopup("Fetching details for $number...")
        scope.launch {
            val prefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
            var uid = prefs.getString("flutter.${Constants.sharedPrefFlutterKey}", null)
            Log.d(TAG, "Shared pref User ID --${uid}")
//            uid = "8kLAdhvEXlgCPtDSFzLAoF6vEZl2"
            if (uid.isNullOrEmpty()) {
                return@launch
            }

            //  Make API call
            when (val result = ApiClient.fetchDialerPopup(uid, number)) {
                is ApiResult.Success -> {
                    withContext(Dispatchers.Main) {
                        updatePopup(result.data)
                    }
                }

                is ApiResult.Failure -> {
                    withContext(Dispatchers.Main) {
                        loader?.visibility = View.GONE
                        dataCard?.visibility = View.VISIBLE
                        Log.d(TAG,"API Failure : ${result.error.message}")
                        nameView?.text = getString(R.string.unknown_caller)
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundNotification() {
        val channelId = "call_popup_service"
        val channel = NotificationChannel(channelId, "Call Popup", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Listening for calls")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            startForeground(1, notification, FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        else
            startForeground(1, notification)
    }

    /**
     * Show the popup UI.
     */
    private fun showPopup(initialText: String) {
        if (!Settings.canDrawOverlays(this)
        ) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        popupView = inflater.inflate(R.layout.call_popup, null)

        nameView = popupView!!.findViewById(R.id.tvName)
        statusView = popupView!!.findViewById(R.id.tvType)
        notesView = popupView!!.findViewById(R.id.tvNote)
        lastActDays = popupView!!.findViewById(R.id.dayCount)
        loader = popupView!!.findViewById(R.id.progressBar)
        dataCard = popupView!!.findViewById(R.id.data_card)
        val closeIcon = popupView!!.findViewById<ImageView>(R.id.ivClose)

        nameView?.text = initialText
        loader?.visibility = View.VISIBLE
        dataCard?.visibility = View.GONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        try {
            windowManager?.addView(popupView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Enable drag
        popupView!!.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var isMoving = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        isMoving = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchX
                        val dy = event.rawY - touchY
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isMoving = true
                            params.x = initialX + dx.toInt()
                            params.y = initialY + dy.toInt()
                            windowManager?.updateViewLayout(popupView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> return isMoving
                }
                return false
            }
        })

        closeIcon.setOnClickListener { destroyPopup() }
    }

    /**
     * Update popup once API result arrives.
     */
    private fun updatePopup(popupInfo: DialerPopupInfo) {
        loader?.visibility = View.GONE
        dataCard?.visibility= View.VISIBLE
        nameView?.text = popupInfo.name
        notesView?.text = if (popupInfo.notes.isNullOrEmpty()) "--No Data--" else popupInfo.notes
        statusView?.text = popupInfo.status
        lastActDays?.text = popupInfo.lastAct.toString()

    }

    private fun destroyPopup() {
        popupView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            popupView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyPopup()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
package com.example.hardware

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ImageReader
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.StatFs
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

object HardwareController {
    private const val TAG = "HardwareController"

    // Karunya Campus Geo-coordinates
    private const val KARUNYA_ACADEMIC_LAT = 10.9360
    private const val KARUNYA_ACADEMIC_LNG = 76.7441
    private const val KARUNYA_HOSTEL_LAT = 10.9315
    private const val KARUNYA_HOSTEL_LNG = 76.7480

    private var activeMediaPlayer: MediaPlayer? = null
    private var activeRecorder: MediaRecorder? = null
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    // --- 1. Ringtone & Alarm with STREAM_ALARM (DND Bypass) ---

    @Synchronized
    fun playAlarmRingtone(context: Context, volumePercent: Int = 100) {
        stopAlarmRingtone()
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val targetVol = ((volumePercent.coerceIn(0, 100) / 100f) * maxVolume).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVol, 0)
            }

            var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            val player = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            activeMediaPlayer = player
            Log.i(TAG, "Alarm ringtone started with STREAM_ALARM at $volumePercent%")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm ringtone: ${e.message}", e)
        }
    }

    @Synchronized
    fun stopAlarmRingtone() {
        try {
            activeMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping alarm: ${e.message}")
        }
        activeMediaPlayer = null
        try {
            com.example.alarm.AlarmController.stopRinging()
        } catch (ignored: Exception) {}
    }

    @Synchronized
    fun stopAlarm() = stopAlarmRingtone()

    // --- 2. Silent Camera Photo Capture (Camera2 API) ---

    fun silentCapturePhoto(context: Context, onResult: (Boolean, String, String?) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onResult(false, "CAMERA permission not granted by user", null)
            return
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            onResult(false, "CameraManager unavailable on this device", null)
            return
        }

        try {
            val cameraIdList = cameraManager.cameraIdList
            if (cameraIdList.isEmpty()) {
                onResult(false, "No camera found on device", null)
                return
            }

            // Prefer back camera, fallback to first available
            var targetCameraId = cameraIdList[0]
            for (id in cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    targetCameraId = id
                    break
                }
            }

            val thread = HandlerThread("CameraBackground").also { it.start() }
            cameraThread = thread
            val handler = Handler(thread.looper)
            cameraHandler = handler

            val imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2)
            var captured = false

            imageReader.setOnImageAvailableListener({ reader ->
                if (captured) return@setOnImageAvailableListener
                captured = true
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        image.close()

                        val outputFile = File(context.cacheDir, "silent_snap_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(outputFile).use { fos ->
                            fos.write(bytes)
                        }
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        Log.i(TAG, "Silent photo captured: ${outputFile.absolutePath} (${bytes.size} bytes)")
                        onResult(true, "Photo captured successfully: ${outputFile.name}", base64)
                    } catch (e: Exception) {
                        onResult(false, "Error saving captured frame: ${e.message}", null)
                    } finally {
                        cleanupCamera(thread)
                    }
                }
            }, handler)

            cameraManager.openCamera(targetCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val surfaceList = listOf(imageReader.surface)
                        @Suppress("DEPRECATION")
                        camera.createCaptureSession(surfaceList, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                    captureBuilder.addTarget(imageReader.surface)
                                    captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                    session.capture(captureBuilder.build(), null, handler)
                                } catch (e: Exception) {
                                    onResult(false, "Failed to send capture request: ${e.message}", null)
                                    camera.close()
                                    cleanupCamera(thread)
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                onResult(false, "Camera capture session configuration failed", null)
                                camera.close()
                                cleanupCamera(thread)
                            }
                        }, handler)
                    } catch (e: Exception) {
                        onResult(false, "Error setting up capture session: ${e.message}", null)
                        camera.close()
                        cleanupCamera(thread)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cleanupCamera(thread)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    onResult(false, "Camera error code: $error", null)
                    camera.close()
                    cleanupCamera(thread)
                }
            }, handler)

        } catch (e: Exception) {
            onResult(false, "Silent photo capture exception: ${e.message}", null)
        }
    }

    private fun cleanupCamera(thread: HandlerThread) {
        try {
            thread.quitSafely()
        } catch (ignored: Exception) {}
    }

    // --- 3. Silent Audio Recording ---

    fun recordAudioClip(context: Context, durationSeconds: Int = 5, onResult: (Boolean, String, String?) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onResult(false, "RECORD_AUDIO permission not granted", null)
            return
        }

        val outputFile = File(context.cacheDir, "audio_clip_${System.currentTimeMillis()}.m4a")
        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            activeRecorder = recorder

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(64000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()

            CoroutineScope(Dispatchers.IO).launch {
                delay((durationSeconds.coerceIn(2, 30)) * 1000L)
                try {
                    recorder.stop()
                    recorder.release()
                    activeRecorder = null
                    val bytes = outputFile.readBytes()
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    Log.i(TAG, "Audio recorded: ${outputFile.name} (${bytes.size} bytes)")
                    onResult(true, "Audio recorded: ${outputFile.name}", base64)
                } catch (e: Exception) {
                    onResult(false, "Failed to finish audio recording: ${e.message}", null)
                }
            }
        } catch (e: Exception) {
            onResult(false, "Failed to initialize audio recorder: ${e.message}", null)
        }
    }

    // --- 4. Device Telemetry & Volume Control ---

    fun getDeviceTelemetry(context: Context): JSONObject {
        val root = JSONObject()
        try {
            // Battery
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val bStatus = context.registerReceiver(null, ifilter)
            val level = bStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 85
            val status = bStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            root.put("battery_percent", batteryPct)
            root.put("is_charging", isCharging)

            // Audio volumes
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                val volumes = JSONObject().apply {
                    put("alarm", audioManager.getStreamVolume(AudioManager.STREAM_ALARM))
                    put("alarm_max", audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM))
                    put("music", audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
                    put("ring", audioManager.getStreamVolume(AudioManager.STREAM_RING))
                    put("notification", audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
                    put("ringer_mode", when (audioManager.ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> "SILENT"
                        AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
                        else -> "NORMAL"
                    })
                }
                root.put("volumes", volumes)
            }

            // Wi-Fi
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ssid = wifiInfo?.ssid?.trim('"') ?: "Unknown"
            root.put("wifi_connected", wifiInfo != null && wifiInfo.networkId != -1)
            root.put("wifi_ssid", ssid)
            root.put("wifi_rssi", wifiInfo?.rssi ?: 0)

            // Storage
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            root.put("storage_free_mb", freeBytes / (1024 * 1024))
            root.put("storage_total_mb", totalBytes / (1024 * 1024))

        } catch (e: Exception) {
            Log.w(TAG, "Telemetry gather error: ${e.message}")
        }
        return root
    }

    fun setDeviceVolume(context: Context, streamType: String, percent: Int): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val stream = when (streamType.lowercase()) {
                "alarm" -> AudioManager.STREAM_ALARM
                "music", "media" -> AudioManager.STREAM_MUSIC
                "ring" -> AudioManager.STREAM_RING
                "notification" -> AudioManager.STREAM_NOTIFICATION
                else -> AudioManager.STREAM_ALARM
            }
            val maxVol = audioManager.getStreamMaxVolume(stream)
            val target = ((percent.coerceIn(0, 100) / 100f) * maxVol).toInt()
            audioManager.setStreamVolume(stream, target, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- 5. Campus Geofence & Location Context ---

    fun getCampusContext(context: Context, currentHour: Int = -1, currentDayOfWeek: Int = -1): JSONObject {
        val root = JSONObject()
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val currentSsid = wifiInfo?.ssid?.trim('"') ?: ""

            val isCampusWifi = currentSsid.contains("Karunya", ignoreCase = true) ||
                    currentSsid.contains("KITS", ignoreCase = true) ||
                    currentSsid.contains("Campus", ignoreCase = true)

            val isHostelWifi = currentSsid.contains("Hostel", ignoreCase = true) ||
                    currentSsid.contains("Bethany", ignoreCase = true) ||
                    currentSsid.contains("Evangeline", ignoreCase = true) ||
                    currentSsid.contains("Father", ignoreCase = true)

            // Try getting location
            var lat = KARUNYA_ACADEMIC_LAT
            var lng = KARUNYA_ACADEMIC_LNG
            var hasGps = false

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) {
                    lat = loc.latitude
                    lng = loc.longitude
                    hasGps = true
                }
            }

            // Calculate distance to academic block and hostel
            val resultsAcademic = FloatArray(1)
            Location.distanceBetween(lat, lng, KARUNYA_ACADEMIC_LAT, KARUNYA_ACADEMIC_LNG, resultsAcademic)
            val distToAcademic = resultsAcademic[0]

            val resultsHostel = FloatArray(1)
            Location.distanceBetween(lat, lng, KARUNYA_HOSTEL_LAT, KARUNYA_HOSTEL_LNG, resultsHostel)
            val distToHostel = resultsHostel[0]

            val isInAcademicBlock = distToAcademic < 350.0 || (isCampusWifi && !isHostelWifi)
            val isInHostel = distToHostel < 300.0 || isHostelWifi

            val hour = if (currentHour >= 0) currentHour else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val isClassHours = hour in 9..16 // 09:00 to 16:30

            // If it's class hours and still in hostel, Hermes warning alarm triggers!
            val shouldTriggerClassWarning = isClassHours && isInHostel

            root.put("campus_name", "Karunya Institute of Technology and Sciences")
            root.put("is_on_campus_wifi", isCampusWifi)
            root.put("wifi_ssid", currentSsid)
            root.put("is_in_hostel", isInHostel)
            root.put("is_in_academic_block", isInAcademicBlock)
            root.put("latitude", lat)
            root.put("longitude", lng)
            root.put("has_real_gps", hasGps)
            root.put("distance_to_classes_meters", distToAcademic.toInt())
            root.put("distance_to_hostel_meters", distToHostel.toInt())
            root.put("is_class_hours", isClassHours)
            root.put("in_class_warning", shouldTriggerClassWarning)
            root.put("warning_message", if (shouldTriggerClassWarning) "WARNING: Class hour active but student remains in hostel!" else "Normal campus status")

        } catch (e: Exception) {
            Log.w(TAG, "Campus context error: ${e.message}")
        }
        return root
    }
}

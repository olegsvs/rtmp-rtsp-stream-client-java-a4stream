package com.pedro.rtpstreamer.backgroundexample

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.rtplibrary.kuzalex.Camera3Base
import com.pedro.rtplibrary.kuzalex.RtmpCamera3
import com.pedro.rtplibrary.view.OpenGlView
import com.pedro.rtpstreamer.R
import java.lang.Long.signum
import java.util.*


internal class CompareSizesByArea : Comparator<Size> {

  // We cast here to ensure the multiplications won't overflow
  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  override fun compare(lhs: Size, rhs: Size) =
          signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)

}

/**
 * Basic RTMP/RTSP service streaming implementation with camera2
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class RtpService : Service() {

  private var endpoint: String? = null

  override fun onCreate() {
    super.onCreate()
    Log.e(TAG, "RTP service create")
    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH)
      notificationManager?.createNotificationChannel(channel)
    }
    keepAliveTrick()
  }

  private fun keepAliveTrick() {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
      val notification = NotificationCompat.Builder(this, channelId)
          .setOngoing(true)
          .setContentTitle("")
          .setContentText("").build()
      startForeground(1, notification)
    } else {
      startForeground(1, Notification())
    }
  }

  override fun onBind(p0: Intent?): IBinder? {
    return null
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.e(TAG, "RTP service started")
    endpoint = intent?.extras?.getString("endpoint")
    if (endpoint != null) {
//      prepareStreamRtp()
      startStreamRtp(endpoint!!)
    }
    return START_STICKY
  }

  companion object {
    private val TAG = "RtpService"
    private val channelId = "rtpStreamChannel"
    private val notifyId = 123456
    private var notificationManager: NotificationManager? = null
    private var camera3Base: Camera3Base? = null
    private var openGlView: OpenGlView? = null
    private var contextApp: Context? = null


    private var lastPreviewWidth : Int? = null
    private var lastPreviewHeight : Int? = null
    private var lastRotation : Int? = null


//    val encoderWidth = 640
//    val encoderHeight = 480

//    val encoderWidth = 1280
//    val encoderHeight = 720
    val encoderWidth = 1280
    val encoderHeight = 960
//    val encoderWidth = 176
//    val encoderHeight = 144
//    val encoderWidth = 720
//    val encoderHeight = 720
//    val encoderWidth = 1024
//    val encoderHeight = 768


    fun switchCamera() {
      camera3Base?.switchCamera()


      camera3Base?.let { cam ->

        if (cam.isOnPreview && lastPreviewWidth!=null && lastPreviewHeight!=null && lastRotation!=null) {
          cam.setupPreviewSurface(cam.surface,  lastRotation!!)
        }
      }
    }

    private var test = false
    fun test() {
//      if (test){
//        test=!test
//        camera2Base?.cameraManager?.testStartRepeatingEncoder1()
//
//      } else {
//        test=!test
//        camera2Base?.cameraManager?.testStopRepeatingEncoder1()
//
//
//      }

    }


//    private fun chooseOptimalSize(outputSizes: MutableList<Size>, width: Int, height: Int): Size? {
//      val preferredRatio = width / height.toDouble()
//      var currentOptimalSize = outputSizes[0]
//      var currentOptimalRatio = currentOptimalSize.width / currentOptimalSize.height.toDouble()
//      for (currentSize in outputSizes) {
//        val currentRatio = currentSize.width / currentSize.height.toDouble()
//        if (Math.abs(preferredRatio - currentRatio) <
//                Math.abs(preferredRatio - currentOptimalRatio)) {
//          currentOptimalSize = currentSize
//          currentOptimalRatio = currentRatio
//        }
//      }
//      return currentOptimalSize
//    }

    // Finds the closest Size to (|width|x|height|) in |sizes|, and returns it or null.
    // Ignores |width| or |height| if either is zero (== don't care).
    private fun findClosestSizeInArray(sizes: MutableList<Size>, width: Int, height: Int): Size? {
      if (sizes == null) return null
      var closestSize: Size? = null
      var minDiff = Int.MAX_VALUE
      for (size in sizes) {
        val diff = ((if (width > 0) Math.abs(size.width - width) else 0)
                + if (height > 0) Math.abs(size.height - height) else 0)
        if (diff < minDiff) {
          minDiff = diff
          closestSize = size
        }
      }
      if (minDiff == Int.MAX_VALUE) {
        Log.e(TAG, "Couldn't find resolution close to ($width x $height)")
        return null
      }
      return closestSize
    }





    fun addPreview(surface: Surface, width: Int, height: Int){


      val rotation = CameraHelper.getCameraOrientation(contextApp)



      val largest = Collections.max(
              camera3Base?.resolutionsFront,
              CompareSizesByArea())
      Log.i(TAG, "$largest")


      camera3Base?.resolutionsFront?.let {
        val sz =  findClosestSizeInArray(it, Math.max(encoderWidth, encoderHeight), Math.min(encoderWidth, encoderHeight))
        Log.i(TAG, "$sz")
      }


      camera3Base?.let { cam ->

        if (cam.isOnPreview) {
          cam.setupPreviewSurface(surface, rotation)
          lastPreviewWidth = width
          lastPreviewHeight = height
          lastRotation = rotation

        } else {

          camera3Base!!.setupAndStartPreview(
                  CameraHelper.Facing.BACK,
                  width, height,
                  encoderWidth, encoderHeight, rotation,
          )
          cam.setupPreviewSurface(surface, rotation)
          lastPreviewWidth = width
          lastPreviewHeight = height
          lastRotation = rotation
        }
      }
    }

    fun removePreview(){
      camera3Base?.let {
        if (it.isOnPreview) {
          it.removePreviewSurface()
        } else {
        }
      }

    }

    private fun startStreamRtp(endpoint: String) {

      if (camera3Base!!.isStreaming)
        return




      if (!camera3Base!!.prepareVideo(encoderWidth, encoderHeight, 30, 1200 * 1024, 2, camera3Base!!.encoderRotation, -1, -1) )
        return

      if (!camera3Base!!.prepareAudio())
        return


      camera3Base!!.startStream(endpoint)
    }





    fun init(context: Context) {
      contextApp = context
      if (camera3Base == null) {
        camera3Base = RtmpCamera3(context, connectCheckerRtp)


      }
    }

    fun stop() {
      if (camera3Base != null) {
        camera3Base!!.stop()
      }
    }






    private val connectCheckerRtp = object : ConnectCheckerRtp {
      override fun onConnectionSuccessRtp() {
        showNotification("Stream started")
        Log.e(TAG, "RTP service destroy")
      }

      override fun onNewBitrateRtp(bitrate: Long) {

      }

      override fun onConnectionFailedRtp(reason: String) {
        showNotification("Stream connection failed")
        Log.e(TAG, "RTP service destroy")
      }

      override fun onDisconnectRtp() {
        showNotification("Stream stopped")
      }

      override fun onAuthErrorRtp() {
        showNotification("Stream auth error")
      }

      override fun onAuthSuccessRtp() {
        showNotification("Stream auth success")
      }
    }

    private fun showNotification(text: String) {
      contextApp?.let {
        val notification = NotificationCompat.Builder(it, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("RTP Stream")
            .setContentText(text).build()
        notificationManager?.notify(notifyId, notification)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.e(TAG, "RTP service destroy")
    camera3Base?.stopStream()
  }

}



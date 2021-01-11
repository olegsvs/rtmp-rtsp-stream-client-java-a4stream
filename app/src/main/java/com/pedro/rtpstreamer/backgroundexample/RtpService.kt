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
import com.pedro.encoder.input.gl.render.filters.RotationFilterRender
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.rtplibrary.base.Camera3Base
import com.pedro.rtplibrary.base.RtmpCamera3
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


    val encoderWidth = 1280
    val encoderHeight = 720



    fun switchCamera() {
      camera3Base?.switchCamera()
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







    fun addPreview(surface: Surface, width: Int, height: Int){


      val rotation = CameraHelper.getCameraOrientation(contextApp)


      camera3Base?.let { cam ->

        if (cam.isOnPreview) {
          cam.setupPreviewSurface(surface, width, height, rotation)

        } else {

          camera3Base!!.setupAndStartPreview(
                  CameraHelper.Facing.FRONT,
                  width, height, CameraHelper.getCameraOrientation(contextApp),
                  encoderWidth, encoderHeight, rotation,
          )
          cam.setupPreviewSurface(surface, width, height, rotation)
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


//      val rotation = CameraHelper.getCameraOrientation(contextApp)

//      if (camera3Base!!.encoderRotation != rotation) {
//
//        val s = camera3Base!!.surface
//        val w = camera3Base!!.previewWidth
//        val h = camera3Base!!.previewHeight
//
//        this.stop()
//
//        this.addPreview(s, w, h)
//
//      }

      if (!camera3Base!!.prepareVideo(encoderWidth, encoderHeight, 30, 1200*1024, 2, camera3Base!!.encoderRotation, -1, -1) )
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


//        val largest = Collections.max(
//                camera3Base?.resolutionsFront,
//                CompareSizesByArea())
//
//        Log.i(TAG, "$largest")
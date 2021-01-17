package com.pedro.rtpstreamer.backgroundexample

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.rtpstreamer.R
import kotlinx.android.synthetic.main.activity_background.*

class BackgroundActivity : AppCompatActivity(), SurfaceHolder.Callback {





  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)



    requestWindowFeature(Window.FEATURE_NO_TITLE);


    setContentView(R.layout.activity_background)
    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);



    getSupportActionBar()?.hide();

    et_rtp_url.setText("rtmp://192.168.1.199/live/one")
    et_rtp_url.setText("rtmp://flutter-webrtc.kuzalex.com/live/one")
    RtpService.init(this)
    b_start_stop.setOnClickListener {
      if (isMyServiceRunning(RtpService::class.java)) {
        stopService(Intent(applicationContext, RtpService::class.java))
        b_start_stop.setText(R.string.start_button)
      } else {
        val intent = Intent(applicationContext, RtpService::class.java)
        intent.putExtra("endpoint", et_rtp_url.text.toString())
        startService(intent)
        b_start_stop.setText(R.string.stop_button)
      }
    }



    switch_camera.setOnClickListener {
      try {
        RtpService.switchCamera()
      } catch (e: CameraOpenException) {
        Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
      }
    }

    b_test.setOnClickListener {
      try {
        RtpService.test()
      } catch (e: CameraOpenException) {
        Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
      }
    }

    surfaceView.holder.addCallback(this)
  }

  override fun onDestroy() {
    if (!isMyServiceRunning(RtpService::class.java)) {
      RtpService.stop()
    }
    super.onDestroy()
  }


  override fun surfaceChanged(holder: SurfaceHolder, p1: Int, p2: Int, p3: Int) {

    Log.e("kuzalex", "surfaceChanged")
    RtpService.addPreview(holder.surface, p2, p3)
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {

    Log.e("kuzalex", "surfaceDestroyed")

    RtpService.removePreview()
  }

  override fun surfaceCreated(holder: SurfaceHolder) {

  }

  override fun onResume() {
    super.onResume()
    if (isMyServiceRunning(RtpService::class.java)) {
      b_start_stop.setText(R.string.stop_button)
    } else {
      b_start_stop.setText(R.string.start_button)
    }
  }

  @Suppress("DEPRECATION")
  private fun isMyServiceRunning(serviceClass: Class<*>): Boolean {
    val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
      if (serviceClass.name == service.service.className) {
        return true
      }
    }
    return false
  }
}

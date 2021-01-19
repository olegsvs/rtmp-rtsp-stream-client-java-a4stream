package com.pedro.rtpstreamer.backgroundexample

import android.app.ActivityManager
import android.content.Context
import android.content.Intent

import android.content.res.Configuration
import android.opengl.EGL14
import android.opengl.GLES20

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.rtpstreamer.R
import kotlinx.android.synthetic.main.activity_background.*
import javax.microedition.khronos.egl.*


class BackgroundActivity : AppCompatActivity(), SurfaceHolder.Callback {





  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)



    requestWindowFeature(Window.FEATURE_NO_TITLE);


    setContentView(R.layout.activity_background)
    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);



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
        RtpService.removePreview()

//        RtpService.test()
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

  private fun clearSurface(surface: Surface) {
    val egl = EGLContext.getEGL() as EGL10
    val display: EGLDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
    egl.eglInitialize(display, null)
    val attribList = intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL10.EGL_NONE, 0,  // placeholder for recordable [@-3]
            EGL10.EGL_NONE
    )
    val configs: Array<EGLConfig?> = arrayOfNulls<EGLConfig>(1)
    val numConfigs = IntArray(1)
    egl.eglChooseConfig(display, attribList, configs, configs.size, numConfigs)
    val config: EGLConfig? = configs[0]
    val context: EGLContext = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL10.EGL_NONE
    ))
    val eglSurface: EGLSurface = egl.eglCreateWindowSurface(display, config, surface, intArrayOf(
            EGL14.EGL_NONE
    ))
    egl.eglMakeCurrent(display, eglSurface, eglSurface, context)
    GLES20.glClearColor(0f, 0f, 0f, 1f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    egl.eglSwapBuffers(display, eglSurface)
    egl.eglDestroySurface(display, eglSurface)
    egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
            EGL10.EGL_NO_CONTEXT)
    egl.eglDestroyContext(display, context)
    egl.eglTerminate(display)
  }

  var a:Surface? = null

  @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    // Checks the orientation of the screen
    if (newConfig.orientation === Configuration.ORIENTATION_LANDSCAPE) {
      Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show()
    } else if (newConfig.orientation === Configuration.ORIENTATION_PORTRAIT) {
      Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show()
    }
    RtpService.removePreview()

    a?.let { clearSurface(it) }

  }

  override fun surfaceChanged(holder: SurfaceHolder, p1: Int, p2: Int, p3: Int) {

    Log.e("kuzalex", "surfaceChanged")
    RtpService.addPreview(holder.surface, p2, p3)
    a=holder.surface
  }

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
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

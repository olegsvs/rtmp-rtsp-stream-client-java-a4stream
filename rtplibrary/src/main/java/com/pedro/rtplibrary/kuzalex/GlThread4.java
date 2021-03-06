package com.pedro.rtplibrary.kuzalex;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.input.gl.SurfaceManager;
import com.pedro.encoder.input.gl.render.ManagerRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.input.video.FpsLimiter;
import com.pedro.encoder.utils.gl.GlUtil;
import com.pedro.rtplibrary.view.Filter;
import com.pedro.rtplibrary.view.GlInterface;
import com.pedro.rtplibrary.view.TakePhotoCallback;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;




/**
 * Created by pedro on 4/03/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GlThread4
        implements  Runnable, SurfaceTexture.OnFrameAvailableListener {

    private final Context context;
    private Thread thread = null;
    private boolean frameAvailable = false;
    private boolean running = true;
    private boolean initialized = false;

    private SurfaceManager surfaceManager = null;
    private SurfaceManager surfaceManagerEncoder1 = null;
    private SurfaceManager surfaceManagerEncoder2 = null;

    private ManagerRender4 textureManager = null;

    private final Semaphore semaphore = new Semaphore(0);
    private final BlockingQueue<Filter> filterQueue1 = new LinkedBlockingQueue<>();
    private final BlockingQueue<Filter> filterQueue2 = new LinkedBlockingQueue<>();
    private final Object sync = new Object();
    private int encoderWidth, encoderHeight;
    private int previewWidth, previewHeight;
//    private boolean loadAA = false;
    private int streamRotation;
    private boolean muteVideoEncode = false;
    private boolean muteVideoPreview = false;
    private boolean isStreamHorizontalFlip = false;
    private boolean isStreamVerticalFlip = false;

    private boolean AAEnabled = false;
    private FpsLimiter fpsLimiter = new FpsLimiter();
    //used with camera
    private TakePhotoCallback takePhotoCallback;
    private boolean forceRender = false;

    public GlThread4(Context context) {
        this.context = context;
    }

    public void init() {
        if (!initialized) textureManager = new ManagerRender4();
        textureManager.setCameraFlip(false, false);
        initialized = true;
    }


    public void setForceRender(boolean forceRender) {
        this.forceRender = forceRender;
    }


    public void setSize(int encoderWidth, int encoderHeight, int previewWidth, int previewHeight) {
        this.encoderWidth = encoderWidth;
        this.encoderHeight = encoderHeight;
        this.previewWidth=previewWidth;
        this.previewHeight=previewHeight;
    }



    public void muteVideoEncode() {
        muteVideoEncode = true;
    }

    public void unMuteVideoEncode() {
        muteVideoEncode = false;
    }

    public void muteVideoPreview() {
        muteVideoPreview = true;
    }

    public void unMuteVideoPreview() {
        muteVideoPreview = false;
    }


    public void setFps(int fps) {
        fpsLimiter.setFPS(fps);
    }

    public SurfaceTexture getSurfaceTexture() {
        return textureManager.getSurfaceTexture();
    }

    public Surface getSurface() {
        return textureManager.getSurface();
    }

    public void addMediaCodecSurface1(Surface surface) {
        synchronized (sync) {

            surfaceManagerEncoder1 = new SurfaceManager(surface, surfaceManager);
        }
    }
    public void addMediaCodecSurface2(Surface surface) {
        synchronized (sync) {

            surfaceManagerEncoder2 = new SurfaceManager(surface, surfaceManager);
        }
    }

    public void removeMediaCodecSurface1() {
        synchronized (sync) {
            if (surfaceManagerEncoder1 != null) {
                surfaceManagerEncoder1.release();
                surfaceManagerEncoder1 = null;
            }

        }
    }

    public void removeMediaCodecSurface2() {
        synchronized (sync) {
            if (surfaceManagerEncoder2 != null) {
                surfaceManagerEncoder2.release();
                surfaceManagerEncoder2 = null;
            }

        }
    }

    public void takePhoto(TakePhotoCallback takePhotoCallback) {
        this.takePhotoCallback = takePhotoCallback;
    }

    public void setFilter1(int filterPosition, BaseFilterRender baseFilterRender) {
        filterQueue1.add(new Filter(filterPosition, baseFilterRender));
    }
    public void setFilter1(BaseFilterRender baseFilterRender) {
        setFilter1(0, baseFilterRender);
    }

    public void setFilter2(int filterPosition, BaseFilterRender baseFilterRender) {
        filterQueue2.add(new Filter(filterPosition, baseFilterRender));
    }
    public void setFilter2(BaseFilterRender baseFilterRender) {
        setFilter2(0, baseFilterRender);
    }

//    public void enableAA(boolean AAEnabled) {
//        this.AAEnabled = AAEnabled;
//        loadAA = true;
//    }

    public void setRotation(int rotation) {
        textureManager.setCameraRotation(rotation);
    }

    public void setStreamRotation(int rotation) {
        streamRotation = rotation;
    }

    public void setIsStreamHorizontalFlip(boolean flip) {
        isStreamHorizontalFlip = flip;
    }

    public void setIsStreamVerticalFlip(boolean flip) {
        isStreamVerticalFlip = flip;
    }

//    public boolean isAAEnabled() {
//        return textureManager != null && textureManager.isAAEnabled();
//    }

    public void start() {
        synchronized (sync) {
            thread = new Thread(this);
            running = true;
            thread.start();
            semaphore.acquireUninterruptibly();
        }
    }


    public void stop() {
        synchronized (sync) {
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join(100);
                } catch (InterruptedException e) {
                    thread.interrupt();
                }
                thread = null;
            }
            running = false;
        }
    }

    private void releaseSurfaceManager() {
        if (surfaceManager != null) {
            surfaceManager.release();
            surfaceManager = null;
        }
    }

    @Override
    public void run() {
        releaseSurfaceManager();
        surfaceManager = new SurfaceManager();
        surfaceManager.makeCurrent();
        textureManager.initGl(context, encoderWidth, encoderHeight, encoderWidth, encoderHeight);
        textureManager.getSurfaceTexture().setOnFrameAvailableListener(this);

        semaphore.release();

        ///
        ///
        long kuzalex_startTS = System.currentTimeMillis();
        long kuzalex_nframes = 0;



        try {
            while (running) {
                if (frameAvailable || forceRender) {

                    kuzalex_nframes++;
                    if (System.currentTimeMillis() - kuzalex_startTS > 2000) {
                        // print
                        double a = 1.0 * kuzalex_nframes / (System.currentTimeMillis() - kuzalex_startTS) * 1000;
                        Log.e("kuzalex", "FPS="+a);

                        kuzalex_nframes=0;
                        kuzalex_startTS = System.currentTimeMillis();
                    }

                    if (!filterQueue1.isEmpty()) {
                        Filter filter = filterQueue1.take();
                        textureManager.setFilter1(filter.getPosition(), filter.getBaseFilterRender());
                    }

                    if (!filterQueue2.isEmpty()) {
                        Filter filter = filterQueue2.take();
                        textureManager.setFilter2(filter.getPosition(), filter.getBaseFilterRender());
                    }

                    frameAvailable = false;
                    surfaceManager.makeCurrent();
                    textureManager.updateFrame();
                    textureManager.drawOffScreen();
                    textureManager.drawScreen1(previewWidth, previewHeight, false, 0, 0, true, false, false);
                    textureManager.drawScreen2(encoderWidth, encoderHeight, false, 0, 0, true, false, false);
                    surfaceManager.swapBuffer();

                    synchronized (sync) {
                        if (surfaceManagerEncoder1 != null ) {
                            surfaceManagerEncoder1.makeCurrent();
                            if (muteVideoPreview) {
                                textureManager.drawScreen1(0, 0, false, 0, streamRotation, false,
                                        isStreamVerticalFlip, isStreamHorizontalFlip);
                            } else {
                                textureManager.drawScreen1(previewWidth, previewHeight, false, 0, streamRotation,
                                        false, isStreamVerticalFlip, isStreamHorizontalFlip);
                            }
                            //Necessary use surfaceManagerEncoder because preview manager size in background is 1x1.
                        }

                        if (surfaceManagerEncoder1 != null) surfaceManagerEncoder1.swapBuffer();

                        if (surfaceManagerEncoder2 != null && !fpsLimiter.limitFPS()) {
                            surfaceManagerEncoder2.makeCurrent();
                            if (muteVideoEncode) {
                                textureManager.drawScreen2(0, 0, false, 0, streamRotation, false,
                                        isStreamVerticalFlip, isStreamHorizontalFlip);
                            } else {
                                textureManager.drawScreen2(encoderWidth, encoderHeight, false, 0, streamRotation,
                                        false, isStreamVerticalFlip, isStreamHorizontalFlip);
                            }
                            //Necessary use surfaceManagerEncoder because preview manager size in background is 1x1.
                        }

                        if (surfaceManagerEncoder2 != null) surfaceManagerEncoder2.swapBuffer();
                    }
                }
            }
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        } finally {
            textureManager.release();
            releaseSurfaceManager();
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (sync) {
            frameAvailable = true;
            sync.notifyAll();
        }
    }
}

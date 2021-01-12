package com.pedro.rtplibrary.base;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.pedro.encoder.Frame;
import com.pedro.encoder.audio.AudioEncoder;
import com.pedro.encoder.audio.GetAacData;
import com.pedro.encoder.input.audio.CustomAudioEffect;
import com.pedro.encoder.input.audio.GetMicrophoneData;
import com.pedro.encoder.input.audio.MicrophoneManager;
import com.pedro.encoder.input.audio.MicrophoneManagerManual;
import com.pedro.encoder.input.audio.MicrophoneMode;
import com.pedro.encoder.input.video.Camera2ApiManager;
import com.pedro.encoder.input.video.CameraCallbacks;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.encoder.input.video.CameraOpenException;
import com.pedro.encoder.utils.CodecUtil;
import com.pedro.encoder.video.FormatVideoEncoder;
import com.pedro.encoder.video.GetVideoData;
import com.pedro.encoder.video.VideoEncoder;
import com.pedro.rtplibrary.util.FpsListener;
import com.pedro.rtplibrary.util.RecordController;
import com.pedro.rtplibrary.view.GlInterface;
import com.pedro.rtplibrary.view.LightOpenGlView;
import com.pedro.rtplibrary.view.OffScreenGlThread;
import com.pedro.rtplibrary.view.OpenGlView;

import net.ossrs.rtmp.ConnectCheckerRtmp;
import net.ossrs.rtmp.SrsFlvMuxer;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;


@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public abstract class Camera3Base implements GetAacData, GetVideoData, GetMicrophoneData {


    private final String TAG = "Camera3Base";

    protected Context context;
    public Camera3ApiManager cameraManager;
    protected VideoEncoder videoEncoder;
    private MicrophoneManager microphoneManager;
    private AudioEncoder audioEncoder;

    private boolean streaming = false;

    private GlInterface glCodecInterface;
    private GlInterface glPreviewInterface;

    private boolean videoEnabled = false;
    private boolean onPreview = false;
    private Boolean surfaceAttached = false;

    //    private boolean isBackground = false;
//    protected RecordController recordController;
    private int previewWidth, previewHeight;
    private int encoderWidth,  encoderHeight;
    private int encoderRotation = -1;

    public int getEncoderRotation() {
        return encoderRotation;
    }

    public int getPreviewWidth() {
        return previewWidth;
    }

    public int getPreviewHeight() {
        return previewHeight;
    }

    Surface surface;

    public Surface getSurface() {
        return surface;
    }



    private FpsListener fpsListener = new FpsListener();


    public Camera3Base(Context context) {
        this.context = context;
        glCodecInterface = new OffScreenGlThread(context);
        glCodecInterface.init();

        glPreviewInterface = new OffScreenGlThread(context);
        glPreviewInterface.init();


        cameraManager = new Camera3ApiManager(context);
        videoEncoder = new VideoEncoder(this);
        setMicrophoneMode(MicrophoneMode.ASYNC);

//        isBackground = true;
        init(context);
    }

    private void init(Context context) {

    }

    /**
     * Must be called before prepareAudio.
     *
     * @param microphoneMode mode to work accord to audioEncoder. By default ASYNC:
     * SYNC using same thread. This mode could solve choppy audio or AudioEncoder frame discarded.
     * ASYNC using other thread.
     */
    public void setMicrophoneMode(MicrophoneMode microphoneMode) {
        switch (microphoneMode) {
            case SYNC:
                microphoneManager = new MicrophoneManagerManual();
                audioEncoder = new AudioEncoder(this);
                audioEncoder.setGetFrame(((MicrophoneManagerManual) microphoneManager).getGetFrame());
                break;
            case ASYNC:
                microphoneManager = new MicrophoneManager(this);
                audioEncoder = new AudioEncoder(this);
                break;
        }
    }

    public void setCameraCallbacks(CameraCallbacks callbacks) {
        cameraManager.setCameraCallbacks(callbacks);
    }

    /**
     * Set an audio effect modifying microphone's PCM buffer.
     */
    public void setCustomAudioEffect(CustomAudioEffect customAudioEffect) {
        microphoneManager.setCustomAudioEffect(customAudioEffect);
    }

    /**
     * @param callback get fps while record or stream
     */
    public void setFpsListener(FpsListener.Callback callback) {
        fpsListener.setCallback(callback);
    }

    /**
     * Experimental
     */
    public void enableFaceDetection(Camera2ApiManager.FaceDetectorCallback faceDetectorCallback) {
        cameraManager.enableFaceDetection(faceDetectorCallback);
    }

    /**
     * Experimental
     */
    public void disableFaceDetection() {
        cameraManager.disableFaceDetection();
    }

    /**
     * Experimental
     */
    public boolean isFaceDetectionEnabled() {
        return cameraManager.isFaceDetectionEnabled();
    }

    public boolean isFrontCamera() {
        return cameraManager.isFrontCamera();
    }

    public void enableLantern() throws Exception {
        cameraManager.enableLantern();
    }

    public void disableLantern() {
        cameraManager.disableLantern();
    }

    public boolean isLanternEnabled() {
        return cameraManager.isLanternEnabled();
    }

    public boolean isLanternSupported() {
        return cameraManager.isLanternSupported();
    }

    public void enableAutoFocus() {
        cameraManager.enableAutoFocus();
    }

    public void disableAutoFocus() {
        cameraManager.disableAutoFocus();
    }

    public boolean isAutoFocusEnabled() {
        return cameraManager.isAutoFocusEnabled();
    }

    /**
     * Basic auth developed to work with Wowza. No tested with other server
     *
     * @param user auth.
     * @param password auth.
     */
    public abstract void setAuthorization(String user, String password);

    /**
     * Call this method before use @startStream. If not you will do a stream without video.
     *
     * @param width resolution in px.
     * @param height resolution in px.
     * @param fps frames per second of the stream.
     * @param bitrate H264 in bps.
     * @param rotation could be 90, 180, 270 or 0 (Normally 0 if you are streaming in landscape or 90
     * if you are streaming in Portrait). This only affect to stream result. NOTE: Rotation with
     * encoder is silence ignored in some devices.
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a H264 encoder).
     */
    public boolean prepareVideo(int width, int height, int fps, int bitrate, int iFrameInterval,
                                int rotation, int avcProfile, int avcProfileLevel) {
//        if (onPreview && !(glInterface != null && width == previewWidth && height == previewHeight)) {
//            stopPreview();
//            onPreview = true;
//        }
        boolean result =
                videoEncoder.prepareVideoEncoder(width, height, fps, bitrate, rotation, iFrameInterval,
                        FormatVideoEncoder.SURFACE, avcProfile, avcProfileLevel);
        return result;
    }
//
//    public boolean prepareVideo(int width, int height, int fps, int bitrate, int iFrameInterval,
//                                int rotation) {
//        return prepareVideo(width, height, fps, bitrate, iFrameInterval, rotation, -1, -1);
//    }

    /**
     * backward compatibility reason
     */
//    public boolean prepareVideo(int width, int height, int fps, int bitrate, int rotation) {
//        return prepareVideo(width, height, fps, bitrate, 2, rotation);
//    }
//
//    public boolean prepareVideo(int width, int height, int bitrate) {
//        int rotation = CameraHelper.getCameraOrientation(context);
//        return prepareVideo(width, height, 30, bitrate, 2, rotation);
//    }

    protected abstract void prepareAudioRtp(boolean isStereo, int sampleRate);

    /**
     * Call this method before use @startStream. If not you will do a stream without audio.
     *
     * @param bitrate AAC in kb.
     * @param sampleRate of audio in hz. Can be 8000, 16000, 22500, 32000, 44100.
     * @param isStereo true if you want Stereo audio (2 audio channels), false if you want Mono audio
     * (1 audio channel).
     * @param echoCanceler true enable echo canceler, false disable.
     * @param noiseSuppressor true enable noise suppressor, false  disable.
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a AAC encoder).
     */
    public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo, boolean echoCanceler,
                                boolean noiseSuppressor) {
        if (!microphoneManager.createMicrophone(sampleRate, isStereo, echoCanceler, noiseSuppressor)) {
            return false;
        }
        prepareAudioRtp(isStereo, sampleRate);
        return audioEncoder.prepareAudioEncoder(bitrate, sampleRate, isStereo,
                microphoneManager.getMaxInputSize());
    }

    public boolean prepareAudio(int bitrate, int sampleRate, boolean isStereo) {
        return prepareAudio(bitrate, sampleRate, isStereo, false, false);
    }

    /**
     * Same to call: isHardwareRotation = true; if (openGlVIew) isHardwareRotation = false;
     * prepareVideo(640, 480, 30, 1200 * 1024, isHardwareRotation, 90);
     *
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a H264 encoder).
     */
//    public boolean prepareVideo() {
//        int rotation = CameraHelper.getCameraOrientation(context);
//        return prepareVideo(1280, 720, 30, 1200 * 1024, rotation);
//    }

    /**
     * Same to call: prepareAudio(64 * 1024, 32000, true, false, false);
     *
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a AAC encoder).
     */
    public boolean prepareAudio() {
        return prepareAudio(64 * 1024, 32000, true, false, false);
    }

    /**
     * @param forceVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     * @param forceAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     */
    public void setForce(CodecUtil.Force forceVideo, CodecUtil.Force forceAudio) {
        videoEncoder.setForce(forceVideo);
        audioEncoder.setForce(forceAudio);
    }





    /**
     * Replace glInterface used on fly. Ignored if you use SurfaceView, TextureView or context without
     * OpenGl.
     */
//    private void replaceGlInterface(GlInterface glInterface) {
//        if (this.glInterface != null && Build.VERSION.SDK_INT >= 18) {
//            if (isStreaming() || isRecording() || isOnPreview()) {
//                cameraManager.closeCamera();
//                this.glInterface.removeMediaCodecSurface();
//                this.glInterface.stop();
//                this.glInterface = glInterface;
//                this.glInterface.init();
//                boolean isPortrait = CameraHelper.isPortrait(context);
//                if (isPortrait) {
//                    this.glInterface.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
//                } else {
//                    this.glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
//                }
//                this.glInterface.setRotation(
//                        videoEncoder.getRotation() == 0 ? 270 : videoEncoder.getRotation() - 90);
//                this.glInterface.start();
//                if (isStreaming() || isRecording()) {
//                    this.glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
//                }
//                cameraManager.prepareCamera(this.glInterface.getSurfaceTexture(), videoEncoder.getWidth(),
//                        videoEncoder.getHeight(), videoEncoder.getFps());
//                cameraManager.openLastCamera();
//            } else {
//                this.glInterface = glInterface;
//                this.glInterface.init();
//            }
//        }
//    }


    public void setupAndStartPreview(
            CameraHelper.Facing cameraFacing,
            int previewWidth, int previewHeight, int previewRotation,
            int encoderWidth, int encoderHeight, int encoderRotation
            ) {
        if (!streaming && !onPreview) {
            this.previewWidth = previewWidth;
            this.previewHeight = previewHeight;
            this.encoderRotation = encoderRotation;





            glPreviewInterface.setEncoderSize(
                    Math.max(previewWidth, previewHeight),
                    Math.max(previewWidth, previewHeight)
            );
            glPreviewInterface.setRotation(previewRotation == 0 ? 270 : previewRotation - 90);
            glPreviewInterface.start();



            if (CameraHelper.isPortrait(context)) {
                this.encoderWidth = encoderHeight;
                this.encoderHeight = encoderWidth;
            } else {
                this.encoderWidth = encoderWidth;
                this.encoderHeight = encoderHeight;
            }

            glCodecInterface.setEncoderSize(this.encoderWidth, this.encoderHeight);

            glCodecInterface.setRotation(encoderRotation == 0 ? 270 : encoderRotation - 90);
            glCodecInterface.start();

            glPreviewInterface.getSurfaceTexture().setDefaultBufferSize(encoderWidth, encoderHeight);
            glCodecInterface.getSurfaceTexture().setDefaultBufferSize(encoderWidth, encoderHeight);

            cameraManager.prepareCamera(glPreviewInterface.getSurfaceTexture(), glCodecInterface.getSurfaceTexture(),  videoEncoder.getFps());

            cameraManager.openCameraFacing(cameraFacing);
            onPreview = true;
            surfaceAttached = false;
        }
    }


    // Finds the closest Size to (|width|x|height|) in |sizes|, and returns it or null.
    // Ignores |width| or |height| if either is zero (== don't care).
    private Size findClosestSizeInArray(Size[] sizes, int width, int height) {
        if (sizes == null) return null;
        Size closestSize = null;
        int minDiff = Integer.MAX_VALUE;
        for (Size size : sizes) {
            final int diff = ((width > 0) ? Math.abs(size.getWidth() - width) : 0)
                    + ((height > 0) ? Math.abs(size.getHeight() - height) : 0);
            if (diff < minDiff) {
                minDiff = diff;
                closestSize = size;
            }
        }
       // Log.e(TAG, "Couldn't find resolution close to ("+width+"x"+height+")");

        if (minDiff == Integer.MAX_VALUE) {
            Log.e(TAG, "Couldn't find resolution close to ("+width+"x"+height+")");
            return null;
        }
        if (closestSize!=null)
            Log.e(TAG, " --- --- --- CLOSEST SIZE IS  ("+closestSize.getWidth()+"x"+closestSize.getHeight()+")");

        return closestSize;
    }



    //FIXME: remove old filter???
    public void setupPreviewSurface(
            Surface surface,
            int previewWidth, int previewHeight, int previewRotation
    )
    {
        if (glPreviewInterface!=null ) {



            double encoderAspect =  1.0 * Math.min(this.encoderHeight, this.encoderWidth) /  Math.max(this.encoderHeight, this.encoderWidth) ;
            double realEncoderAspect = encoderAspect;

            if (cameraManager!=null) {
                //
                // Реальное разрешение может не совпадать с encoderWidth encoderHeight
                // Пытаемся угадать реальное разрешение камеры для правильного расчета aspect rate

                Size closestSize = findClosestSizeInArray(
                        cameraManager.isFrontCamera() ? cameraManager.getCameraResolutionsFront() : cameraManager.getCameraResolutionsBack(),
                        Math.max(encoderWidth, encoderHeight),
                        Math.min(encoderWidth, encoderHeight)
                );
                if (closestSize != null) {
                    realEncoderAspect = 1.0 * Math.min(closestSize.getHeight(), closestSize.getWidth()) / Math.max(closestSize.getHeight(), closestSize.getWidth());
                }

            }



            //
            //
            //   PREVIEW
            //
            //







            double a2 = 1.0 * Math.min(this.previewWidth, this.previewHeight) /  Math.max(this.previewWidth, this.previewHeight) ;

            double scale = realEncoderAspect  ;
            double translate = -1 * (1-a2);

//                double scaleAll = 0.3;
            double scaleAll = 1.0;
//
            MyScaleFilter3 scaleFpreview = new MyScaleFilter3();

            if (previewRotation == 90 || previewRotation == 270) {
                scaleFpreview.setScale((float) (scale * scaleAll), (float) (1.0f * scaleAll), (float) translate, 0.0f);

            } else {
                scaleFpreview.setScale((float) (1.0f * scaleAll), (float) (scale * scaleAll),  0.0f, (float) translate);
            }


            glPreviewInterface.setFilter(scaleFpreview);
            glPreviewInterface.setRotation(previewRotation == 0 ? 270 : previewRotation - 90);


            //
            //
            //   ENCODE
            //
            //


            MyScaleFilter3 scaleFencode = new MyScaleFilter3();

            if (this.encoderHeight > this.encoderWidth) {
                if (CameraHelper.isPortrait(context)) {
                    scaleFencode.setScale(1.0f, (float) ( encoderAspect / realEncoderAspect), (float) 0.0f, 0.0f);
                    Log.e(TAG, "-- CASE 1 --");
                } else {
                    double a = realEncoderAspect;
                    double b = a * a * (encoderAspect / realEncoderAspect);

                    scaleFencode.setScale(1.0f, (float) (1.0f * b), (float) 0.0f, 0.0f);
                    Log.e(TAG, "-- CASE 2 --");
                }
                glCodecInterface.setFilter(scaleFencode);

            } else if (this.encoderHeight < this.encoderWidth) {

                if (CameraHelper.isPortrait(context)) {
                    double a = realEncoderAspect;
                    double b = a * a * (encoderAspect / realEncoderAspect);

                    scaleFencode.setScale((float) (1.0f * b), 1.0f, (float) 0.0f, 0.0f);
                    Log.e(TAG, "-- CASE 3 --");

                } else {
                    scaleFencode.setScale((float)(encoderAspect / realEncoderAspect), (float) (1.0f), (float) 0.0f, 0.0f);
                    Log.e(TAG, "-- CASE 4 --");

                }
                glCodecInterface.setFilter(scaleFencode);

            }

            glCodecInterface.setRotation(previewRotation == 0 ? 270 : previewRotation - 90);




            if (!surfaceAttached) {
                this.surface = surface;
                glPreviewInterface.addMediaCodecSurface(surface);
            }


            surfaceAttached = true;
        } else {
            Log.e(TAG, "addPreviewSurface failed");
        }
    }

    public void removePreviewSurface()
    {
        if (glPreviewInterface!=null) {
            surfaceAttached = false;
            surface = null;
            glPreviewInterface.removeMediaCodecSurface();
        } else {
            Log.e(TAG, "removePreviewSurface failed");
        }
    }







    protected abstract void startStreamRtp(String url);

    /**
     * Need be called after @prepareVideo or/and @prepareAudio. This method override resolution of
     *
     * @param url of the stream like: protocol://ip:port/application/streamName
     *
     * RTSP: rtsp://192.168.1.1:1935/live/pedroSG94 RTSPS: rtsps://192.168.1.1:1935/live/pedroSG94
     * RTMP: rtmp://192.168.1.1:1935/live/pedroSG94 RTMPS: rtmps://192.168.1.1:1935/live/pedroSG94
     * @startPreview to resolution seated in @prepareVideo. If you never startPreview this method
     * startPreview for you to resolution seated in @prepareVideo.
     */
    public void startStream(String url) {
        streaming = true;

        resetVideoEncoder();

//        videoEncoder.start();
        audioEncoder.start();
        microphoneManager.start();

        startStreamRtp(url);
//        onPreview = true;
    }

    /**
     * Stop stream started with @startStream.
     */
    public void stopStream() {
        if (streaming) {
            streaming = false;
            stopStreamRtp();

            microphoneManager.stop();
            glCodecInterface.removeMediaCodecSurface();
            videoEncoder.stop();
            audioEncoder.stop();
        }
    }


    private void resetVideoEncoder() {
        glCodecInterface.removeMediaCodecSurface();

        videoEncoder.reset();

        glCodecInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
//        if (glInterface != null) {
//            glInterface
//        } else {
//            cameraManager.closeCamera();
//            cameraManager.prepareCamera(videoEncoder.getInputSurface(), videoEncoder.getFps());
//            cameraManager.openLastCamera();
//        }
    }

//    private void prepareGlView() {
//        if (glInterface != null && videoEnabled) {
//            if (glInterface instanceof OffScreenGlThread) {
//                glInterface = new OffScreenGlThread(context);
//                glInterface.init();
//            }
//            glInterface.setFps(videoEncoder.getFps());
//            if (videoEncoder.getRotation() == 90 || videoEncoder.getRotation() == 270) {
//                glInterface.setEncoderSize(videoEncoder.getHeight(), videoEncoder.getWidth());
//            } else {
//                glInterface.setEncoderSize(videoEncoder.getWidth(), videoEncoder.getHeight());
//            }
//            int rotation = videoEncoder.getRotation();
//            glInterface.setRotation(rotation == 0 ? 270 : rotation - 90);
//            if (!cameraManager.isRunning() && videoEncoder.getWidth() != previewWidth
//                    || videoEncoder.getHeight() != previewHeight) {
//                glInterface.start();
//            }
//            if (videoEncoder.getInputSurface() != null) {
//                glInterface.addMediaCodecSurface(videoEncoder.getInputSurface());
//            }
//            cameraManager.prepareCamera(glInterface.getSurfaceTexture(), videoEncoder.getWidth(),
//                    videoEncoder.getHeight(), videoEncoder.getFps());
//        }
//    }

    protected abstract void stopStreamRtp();




    public void stop() {
        stopStream();
        glCodecInterface.stop();
        glPreviewInterface.stop();
        cameraManager.closeCamera();
        onPreview = streaming = surfaceAttached = false;
        previewWidth = previewHeight = encoderWidth = encoderHeight = 0;
        surface = null;
//        previewHeight = 0;

        //    public void stopPreview() {
//        if (!isStreaming() && !isRecording() && onPreview && !isBackground) {
//            if (glInterface != null) {
//                glInterface.stop();
//            }
//            cameraManager.closeCamera();
//            onPreview = false;
//            previewWidth = 0;
//            previewHeight = 0;
//        }
//    }
    }

    public boolean reTry(long delay, String reason) {
        boolean result = shouldRetry(reason);
        if (result) {
            reTry(delay);
        }
        return result;
    }

    /**
     * Replace with reTry(long delay, String reason);
     */
    @Deprecated
    public void reTry(long delay) {
        resetVideoEncoder();
        reConnect(delay);
    }

    /**
     * Replace with reTry(long delay, String reason);
     */
    @Deprecated
    public abstract boolean shouldRetry(String reason);

    public abstract void setReTries(int reTries);

    protected abstract void reConnect(long delay);

    //cache control
    public abstract boolean hasCongestion();

    public abstract void resizeCache(int newSize) throws RuntimeException;

    public abstract int getCacheSize();

    public abstract long getSentAudioFrames();

    public abstract long getSentVideoFrames();

    public abstract long getDroppedAudioFrames();

    public abstract long getDroppedVideoFrames();

    public abstract void resetSentAudioFrames();

    public abstract void resetSentVideoFrames();

    public abstract void resetDroppedAudioFrames();

    public abstract void resetDroppedVideoFrames();

    /**
     * Get supported preview resolutions of back camera in px.
     *
     * @return list of preview resolutions supported by back camera
     */
    public List<Size> getResolutionsBack() {
        return Arrays.asList(cameraManager.getCameraResolutionsBack());
    }

    /**
     * Get supported preview resolutions of front camera in px.
     *
     * @return list of preview resolutions supported by front camera
     */
    public List<Size> getResolutionsFront() {
        return Arrays.asList(cameraManager.getCameraResolutionsFront());
    }

    public Range<Integer>[] getSupportedFps() {
        return cameraManager.getSupportedFps();
    }

    /**
     * Get supported properties of the camera
     *
     * @return CameraCharacteristics object
     */
    public CameraCharacteristics getCameraCharacteristics() {
        return cameraManager.getCameraCharacteristics();
    }

    /**
     * Mute microphone, can be called before, while and after stream.
     */
    public void disableAudio() {
        microphoneManager.mute();
    }

    /**
     * Enable a muted microphone, can be called before, while and after stream.
     */
    public void enableAudio() {
        microphoneManager.unMute();
    }

    /**
     * Get mute state of microphone.
     *
     * @return true if muted, false if enabled
     */
    public boolean isAudioMuted() {
        return microphoneManager.isMuted();
    }

    /**
     * Get video camera state
     *
     * @return true if disabled, false if enabled
     */
    public boolean isVideoEnabled() {
        return videoEnabled;
    }

    /**
     * Return max zoom level
     *
     * @return max zoom level
     */
    public float getMaxZoom() {
        return cameraManager.getMaxZoom();
    }

    /**
     * Return current zoom level
     *
     * @return current zoom level
     */
    public float getZoom() {
        return cameraManager.getZoom();
    }

    /**
     * Set zoomIn or zoomOut to camera.
     * Use this method if you use a zoom slider.
     *
     * @param level Expected to be >= 1 and <= max zoom level
     * @see Camera2Base#getMaxZoom()
     */
    public void setZoom(float level) {
        cameraManager.setZoom(level);
    }

    /**
     * Set zoomIn or zoomOut to camera.
     *
     * @param event motion event. Expected to get event.getPointerCount() > 1
     */
    public void setZoom(MotionEvent event) {
        cameraManager.setZoom(event);
    }

    public int getBitrate() {
        return videoEncoder.getBitRate();
    }

    public int getResolutionValue() {
        return videoEncoder.getWidth() * videoEncoder.getHeight();
    }

    public int getStreamWidth() {
        return videoEncoder.getWidth();
    }

    public int getStreamHeight() {
        return videoEncoder.getHeight();
    }

    /**
     * Switch camera used. Can be called on preview or while stream, ignored with preview off.
     *
     * @throws CameraOpenException If the other camera doesn't support same resolution.
     */
    public void switchCamera() throws CameraOpenException {
        if (isStreaming() || onPreview) {
            cameraManager.switchCamera();
        }
    }




    public GlInterface getGlPreviewInterface() { return glPreviewInterface; }




    /**
     * Set video bitrate of H264 in bits per second while stream.
     *
     * @param bitrate H264 in bits per second.
     */
    public void setVideoBitrateOnFly(int bitrate) {
        videoEncoder.setVideoBitrateOnFly(bitrate);
    }

    /**
     * Set limit FPS while stream. This will be override when you call to prepareVideo method. This
     * could produce a change in iFrameInterval.
     *
     * @param fps frames per second
     */
    public void setLimitFPSOnFly(int fps) {
        videoEncoder.setFps(fps);
    }

    /**
     * Get stream state.
     *
     * @return true if streaming, false if not streaming.
     */
    public boolean isStreaming() {
        return streaming;
    }


    /**
     * Get preview state.
     *
     * @return true if enabled, false if disabled.
     */
    public boolean isOnPreview() {
        return onPreview;
    }

    protected abstract void getAacDataRtp(ByteBuffer aacBuffer, MediaCodec.BufferInfo info);

    @Override
    public void getAacData(ByteBuffer aacBuffer, MediaCodec.BufferInfo info) {
//        recordController.recordAudio(aacBuffer, info);
        if (streaming) getAacDataRtp(aacBuffer, info);
    }

    protected abstract void onSpsPpsVpsRtp(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps);

    @Override
    public void onSpsPps(ByteBuffer sps, ByteBuffer pps) {
        if (streaming) onSpsPpsVpsRtp(sps, pps, null);
    }

    @Override
    public void onSpsPpsVps(ByteBuffer sps, ByteBuffer pps, ByteBuffer vps) {
        if (streaming) onSpsPpsVpsRtp(sps, pps, vps);
    }

    protected abstract void getH264DataRtp(ByteBuffer h264Buffer, MediaCodec.BufferInfo info);

    @Override
    public void getVideoData(ByteBuffer h264Buffer, MediaCodec.BufferInfo info) {
        fpsListener.calculateFps();
//        recordController.recordVideo(h264Buffer, info);
        if (streaming) getH264DataRtp(h264Buffer, info);
    }

    @Override
    public void inputPCMData(Frame frame) {
        audioEncoder.inputPCMData(frame);
    }

    @Override
    public void onVideoFormat(MediaFormat mediaFormat) {
//        recordController.setVideoFormat(mediaFormat);
    }

    @Override
    public void onAudioFormat(MediaFormat mediaFormat) {
//        recordController.setAudioFormat(mediaFormat);
    }

    public abstract void setLogs(boolean enable);
}





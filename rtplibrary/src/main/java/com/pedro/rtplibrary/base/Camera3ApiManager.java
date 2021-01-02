package com.pedro.rtplibrary.base;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
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

import com.pedro.encoder.input.video.CameraCallbacks;
import com.pedro.encoder.input.video.CameraHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static com.pedro.encoder.input.video.CameraHelper.*;

/**
 * Created by pedro on 4/03/17.
 *
 * <p>
 * Class for use surfaceEncoder to buffer encoder.
 * Advantage = you can use all resolutions.
 * Disadvantages = you cant control fps of the stream, because you cant know when the inputSurface
 * was renderer.
 * <p>
 * Note: you can use opengl for surfaceEncoder to buffer encoder on devices 21 < API > 16:
 * https://github.com/google/grafika
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Camera3ApiManager extends CameraDevice.StateCallback {

    private final String TAG = "Camera2ApiManager";

    private CameraDevice cameraDevice;
    private Surface surface1;
    private Surface surface2;
    private CameraManager cameraManager;
    private Handler cameraHandler;
    private CameraCaptureSession cameraCaptureSession;
    private boolean prepared = false;
    private int cameraId = -1;
    private boolean isFrontCamera = false;
    private CaptureRequest.Builder builderInputSurface;
    private float fingerSpacing = 0;
    private float zoomLevel = 0f;
    private boolean lanternEnable = false;
    private boolean autoFocusEnabled = true;
    private boolean running = false;
    private int fps = 30;
    private final Semaphore semaphore = new Semaphore(0);
    private CameraCallbacks cameraCallbacks;

    //Face detector
    public interface FaceDetectorCallback {
        void onGetFaces(Face[] faces);
    }

    private com.pedro.encoder.input.video.Camera2ApiManager.FaceDetectorCallback faceDetectorCallback;
    private boolean faceDetectionEnabled = false;
    private int faceDetectionMode;

    public Camera3ApiManager(Context context) {
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    public void prepareCamera(Surface surface1, Surface surface2, int fps) {
        this.surface1 = surface1;
        this.surface2= surface2;
        this.fps = fps;
        prepared = true;
    }


    public void prepareCamera(SurfaceTexture surfaceTexture1, SurfaceTexture surfaceTexture2,  int fps) {


        this.surface1 = new Surface(surfaceTexture1);
        this.surface2 = new Surface(surfaceTexture2);
        this.fps = fps;
        prepared = true;
    }


   // public boolean isPrepared() {
//        return prepared;
//    }

    private void startPreview(CameraDevice cameraDevice) {
        try {
            final List<Surface> listSurfaces = new ArrayList<>();

            listSurfaces.add(surface1);
            listSurfaces.add(surface2);

                   cameraDevice.createCaptureSession(listSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    com.pedro.rtplibrary.base.Camera3ApiManager.this.cameraCaptureSession = cameraCaptureSession;
                    try {
                        CaptureRequest captureRequest = drawSurface(listSurfaces);
                        if (captureRequest != null) {
                            cameraCaptureSession.setRepeatingRequest(captureRequest,
                                    faceDetectionEnabled ? cb : null, cameraHandler);
                            Log.i(TAG, "Camera configured");
                        } else {
                            Log.e(TAG, "Error, captureRequest is null");
                        }
                    } catch (CameraAccessException | NullPointerException e) {
                        Log.e(TAG, "Error", e);
                    } catch (IllegalStateException e) {
                        reOpenCamera(cameraId != -1 ? cameraId : 0);
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    cameraCaptureSession.close();
                    if (cameraCallbacks != null) cameraCallbacks.onCameraError("Configuration failed");
                    Log.e(TAG, "Configuration failed");
                }
            }, cameraHandler);
        } catch (CameraAccessException | IllegalArgumentException e) {
            if (cameraCallbacks != null) {
                cameraCallbacks.onCameraError("Create capture session failed: " + e.getMessage());
            }
            Log.e(TAG, "Error", e);
        } catch (IllegalStateException e) {
            reOpenCamera(cameraId != -1 ? cameraId : 0);
        }
    }



    private CaptureRequest drawSurface(List<Surface> surfaces) {
        try {
            builderInputSurface = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            for (Surface surface : surfaces) if (surface != null) builderInputSurface.addTarget(surface);
            adaptFpsRange(fps, builderInputSurface);
            return builderInputSurface.build();
        } catch (CameraAccessException | IllegalStateException e) {
            Log.e(TAG, "Error", e);
            return null;
        }
    }

    private void adaptFpsRange(int expectedFps, CaptureRequest.Builder builderInputSurface) {
        Range<Integer>[] fpsRanges = getSupportedFps();
        if (fpsRanges != null && fpsRanges.length > 0) {
            Range<Integer> closestRange0 = fpsRanges[0];
            // for fixing https://stackoverflow.com/questions/38370583/getcameracharacteristics-control-ae-available-target-fps-ranges-in-camerachara
            Range<Integer> closestRange = new Range<Integer>(
                    closestRange0.getLower() < 1000 ? closestRange0.getLower() : closestRange0.getLower()/1000,
                    closestRange0.getUpper() < 1000 ? closestRange0.getUpper() : closestRange0.getUpper()/1000);

            int measure = Math.abs(closestRange.getLower() - expectedFps) + Math.abs(
                    closestRange.getUpper() - expectedFps);
            for (Range<Integer> range0 : fpsRanges) {
                Range<Integer> range = new Range<Integer>(
                        range0.getLower() < 1000 ? range0.getLower() : range0.getLower()/1000,
                        range0.getUpper() < 1000 ? range0.getUpper() : range0.getUpper()/1000);

                if (range.getLower() <= expectedFps && range.getUpper() >= expectedFps) {
                    int curMeasure =
                            Math.abs(range.getLower() - expectedFps) + Math.abs(range.getUpper() - expectedFps);
                    if (curMeasure < measure) {
                        closestRange = range;
                        measure = curMeasure;
                    }
                }
            }
            Log.i(TAG, "camera2 fps: " + closestRange.getLower() + " - " + closestRange.getUpper());

            Range<Integer> resRange = new Range<Integer>(
                    fpsRanges[0].getUpper()<1000 ? closestRange.getLower() : closestRange.getLower()*1000,
                    fpsRanges[0].getUpper()<1000 ? closestRange.getUpper() : closestRange.getUpper()*1000);

            builderInputSurface.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, resRange);
        }
    }

    public Range<Integer>[] getSupportedFps() {
        try {
            CameraCharacteristics characteristics = getCameraCharacteristics();
            if (characteristics == null) return null;
            return characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error", e);
            return null;
        }
    }

    public int getLevelSupported() {
        try {
            CameraCharacteristics characteristics = getCameraCharacteristics();
            if (characteristics == null) return -1;
            Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            if (level == null) return -1;
            return level;
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error", e);
            return -1;
        }
    }

    public void openCamera() {
        openCameraBack();
    }

    public void openCameraBack() {
        openCameraFacing(Facing.BACK);
    }

    public void openCameraFront() {
        openCameraFacing(Facing.FRONT);
    }

    public void openLastCamera() {
        if (cameraId == -1) {
            openCameraBack();
        } else {
            openCameraId(cameraId);
        }
    }

    public Size[] getCameraResolutionsBack() {
        return getCameraResolutions(Facing.BACK);
    }

    public Size[] getCameraResolutionsFront() {
        return getCameraResolutions(Facing.FRONT);
    }

    public Size[] getCameraResolutions(Facing facing) {
        try {
            CameraCharacteristics characteristics = getCharacteristicsForFacing(cameraManager, facing);
            if (characteristics == null) {
                return new Size[0];
            }

            StreamConfigurationMap streamConfigurationMap =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (streamConfigurationMap == null) return new Size[0];
            Size[] outputSizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
            return outputSizes != null ? outputSizes : new Size[0];
        } catch (CameraAccessException | NullPointerException e) {
            Log.e(TAG, "Error", e);
            return new Size[0];
        }
    }

    @Nullable
    public CameraCharacteristics getCameraCharacteristics() {
        try {
            return cameraId != -1 ? cameraManager.getCameraCharacteristics(String.valueOf(cameraId))
                    : null;
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error", e);
            return null;
        }
    }

    /**
     * Select camera facing
     *
     * @param selectedCameraFacing - CameraCharacteristics.LENS_FACING_FRONT,
     * CameraCharacteristics.LENS_FACING_BACK,
     * CameraCharacteristics.LENS_FACING_EXTERNAL
     */
    public void openCameraFacing(Facing selectedCameraFacing) {
        try {
            String cameraId = getCameraIdForFacing(cameraManager, selectedCameraFacing);
            if (cameraId != null) {
                openCameraId(Integer.valueOf(cameraId));
            } else {
                Log.e(TAG, "Camera not supported"); // TODO maybe we want to throw some exception here?
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error", e);
        }
    }

    public boolean isLanternSupported() {
        CameraCharacteristics characteristics = getCameraCharacteristics();
        if (characteristics == null) return false;
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        if (available == null) return false;
        return available;
    }

    public boolean isLanternEnabled() {
        return lanternEnable;
    }

    /**
     * @required: <uses-permission android:name="android.permission.FLASHLIGHT"/>
     */
    public void enableLantern() throws Exception {
        CameraCharacteristics characteristics = getCameraCharacteristics();
        if (characteristics == null) return;
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        if (available == null) return;
        if (available) {
            if (builderInputSurface != null) {
                try {
                    builderInputSurface.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
                            faceDetectionEnabled ? cb : null, null);
                    lanternEnable = true;
                } catch (Exception e) {
                    Log.e(TAG, "Error", e);
                }
            }
        } else {
            Log.e(TAG, "Lantern unsupported");
            throw new Exception("Lantern unsupported");
        }
    }

    /**
     * @required: <uses-permission android:name="android.permission.FLASHLIGHT"/>
     */
    public void disableLantern() {
        CameraCharacteristics characteristics = getCameraCharacteristics();
        if (characteristics == null) return;
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        if (available == null) return;
        if (available) {
            if (builderInputSurface != null) {
                try {
                    builderInputSurface.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                    cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
                            faceDetectionEnabled ? cb : null, null);
                    lanternEnable = false;
                } catch (Exception e) {
                    Log.e(TAG, "Error", e);
                }
            }
        }
    }

    public void enableAutoFocus() {
        CameraCharacteristics characteristics = getCameraCharacteristics();
        if (characteristics == null) return;
        int[] supportedFocusModes =
                characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (supportedFocusModes != null) {
            List<Integer> focusModesList = new ArrayList<>();
            for (int i : supportedFocusModes) focusModesList.add(i);
            if (builderInputSurface != null) {
                try {
                    if (focusModesList.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                        builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
                                faceDetectionEnabled ? cb : null, null);
                        autoFocusEnabled = true;
                    } else if (focusModesList.contains(CaptureRequest.CONTROL_AF_MODE_AUTO)) {
                        builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_AUTO);
                        cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
                                faceDetectionEnabled ? cb : null, null);
                        autoFocusEnabled = true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error", e);
                }
            }
        }
    }

    public void disableAutoFocus() {
        CameraCharacteristics characteristics = getCameraCharacteristics();
        if (characteristics == null) return;
        int[] supportedFocusModes =
                characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (supportedFocusModes != null) {
            if (builderInputSurface != null) {
                for (int mode : supportedFocusModes) {
                    try {
                        if (mode == CaptureRequest.CONTROL_AF_MODE_OFF) {
                            builderInputSurface.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_OFF);
                            cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
                                    faceDetectionEnabled ? cb : null, null);
                            autoFocusEnabled = false;
                            return;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error", e);
                    }
                }
            }
        }
    }

    public boolean isAutoFocusEnabled() {
        return autoFocusEnabled;
    }

    public void enableFaceDetection(com.pedro.encoder.input.video.Camera2ApiManager.FaceDetectorCallback faceDetectorCallback) {
        CameraCharacteristics characteristics = getCameraCharacteristics();
        if (characteristics == null) return;
        int[] fd =
                characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        if (fd == null) return;
        Integer maxFD = characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
        if (maxFD == null) return;
        if (fd.length > 0) {
            List<Integer> fdList = new ArrayList<>();
            for (int FaceD : fd) {
                fdList.add(FaceD);
            }
            if (maxFD > 0) {
                this.faceDetectorCallback = faceDetectorCallback;
                faceDetectionEnabled = true;
                faceDetectionMode = Collections.max(fdList);
                setFaceDetect(builderInputSurface, faceDetectionMode);
                prepareFaceDetectionCallback();
            } else {
                Log.e(TAG, "No face detection");
            }
        } else {
            Log.e(TAG, "No face detection");
        }
    }

    public void disableFaceDetection() {
        if (faceDetectionEnabled) {
            faceDetectorCallback = null;
            faceDetectionEnabled = false;
            faceDetectionMode = 0;
            prepareFaceDetectionCallback();
        }
    }

    public boolean isFaceDetectionEnabled() {
        return faceDetectorCallback != null;
    }

    private void setFaceDetect(CaptureRequest.Builder requestBuilder, int faceDetectMode) {
        if (faceDetectionEnabled) {
            requestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, faceDetectMode);
        }
    }

    public void setCameraCallbacks(CameraCallbacks cameraCallbacks) {
        this.cameraCallbacks = cameraCallbacks;
    }

    private void prepareFaceDetectionCallback() {
        try {
            cameraCaptureSession.stopRepeating();
            cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
                    faceDetectionEnabled ? cb : null, null);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error", e);
        }
    }

    private final CameraCaptureSession.CaptureCallback cb =
            new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
                    if (faceDetectorCallback != null) {
                        faceDetectorCallback.onGetFaces(faces);
                    }
                }
            };

    @SuppressLint("MissingPermission")
    public void openCameraId(Integer cameraId) {
        this.cameraId = cameraId;
        if (prepared) {
            HandlerThread cameraHandlerThread = new HandlerThread(TAG + " Id = " + cameraId);
            cameraHandlerThread.start();
            cameraHandler = new Handler(cameraHandlerThread.getLooper());
            try {
                cameraManager.openCamera(cameraId.toString(), this, cameraHandler);
                semaphore.acquireUninterruptibly();
                CameraCharacteristics cameraCharacteristics =
                        cameraManager.getCameraCharacteristics(Integer.toString(cameraId));
                running = true;
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null) return;
                isFrontCamera = LENS_FACING_FRONT == facing;
                if (cameraCallbacks != null) {
                    cameraCallbacks.onCameraChanged(isFrontCamera);
                }
            } catch (CameraAccessException | SecurityException e) {
                if (cameraCallbacks != null) {
                    cameraCallbacks.onCameraError("Open camera " + cameraId + " failed");
                }
                Log.e(TAG, "Error", e);
            }
        } else {
            Log.e(TAG, "Camera2ApiManager need be prepared, Camera2ApiManager not enabled");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void switchCamera() {
        try {
            String cameraId;
            if (cameraDevice == null || isFrontCamera) {
                cameraId = getCameraIdForFacing(cameraManager, Facing.BACK);
            } else {
                cameraId = getCameraIdForFacing(cameraManager, Facing.FRONT);
            }
            if (cameraId == null) cameraId = "0";
            reOpenCamera(Integer.parseInt(cameraId));
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error", e);
        }
    }

    private void reOpenCamera(int cameraId) {
        if (cameraDevice != null) {
            closeCamera(false);

                prepareCamera(surface1, surface2, fps);

            openCameraId(cameraId);
        }
    }

    public float getMaxZoom() {
        CameraCharacteristics characteristics = getCameraCharacteristics();
        if (characteristics == null) return 1;
        Float maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        if (maxZoom == null) return 1;
        return maxZoom;
    }

    public Float getZoom() {
        return zoomLevel;
    }

    public void setZoom(float level) {
        try {
            float maxZoom = getMaxZoom();
            //Avoid out range level
            if (level <= 0f) {
                level = 0.01f;
            } else if (level > maxZoom) level = maxZoom;

            CameraCharacteristics characteristics = getCameraCharacteristics();
            if (characteristics == null) return;
            Rect rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if (rect == null) return;
            //This ratio is the ratio of cropped Rect to Camera's original(Maximum) Rect
            float ratio = 1f / level;
            //croppedWidth and croppedHeight are the pixels cropped away, not pixels after cropped
            int croppedWidth = rect.width() - Math.round((float) rect.width() * ratio);
            int croppedHeight = rect.height() - Math.round((float) rect.height() * ratio);
            //Finally, zoom represents the zoomed visible area
            Rect zoom = new Rect(croppedWidth / 2, croppedHeight / 2, rect.width() - croppedWidth / 2,
                    rect.height() - croppedHeight / 2);
            builderInputSurface.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            cameraCaptureSession.setRepeatingRequest(builderInputSurface.build(),
                    faceDetectionEnabled ? cb : null, null);
            zoomLevel = level;
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error", e);
        }
    }

    public void setZoom(MotionEvent event) {
        float currentFingerSpacing;
        if (event.getPointerCount() > 1) {
            currentFingerSpacing = getFingerSpacing(event);
            float delta = 0.1f;
            float maxZoom = getMaxZoom();
            if (fingerSpacing != 0) {
                float newLevel = zoomLevel;
                if (currentFingerSpacing > fingerSpacing) { //Don't over zoom-in
                    if ((maxZoom - zoomLevel) <= delta) {
                        delta = maxZoom - zoomLevel;
                    }
                    newLevel += delta;
                } else if (currentFingerSpacing < fingerSpacing) { //Don't over zoom-out
                    if ((zoomLevel - delta) < 1f) {
                        delta = zoomLevel - 1f;
                    }
                    newLevel -= delta;
                }
                setZoom(newLevel);
            }
            fingerSpacing = currentFingerSpacing;
        }
    }

    public boolean isFrontCamera() {
        return isFrontCamera;
    }

    private void resetCameraValues() {
        lanternEnable = false;
        zoomLevel = 1.0f;
    }

//    public void stopRepeatingEncoder() {
//        if (cameraCaptureSession != null) {
//            try {
//                cameraCaptureSession.stopRepeating();
//                surfaceEncoder = null;
//                Surface preview = addPreviewSurface();
//                if (preview != null) {
//                    CaptureRequest captureRequest = drawSurface(Collections.singletonList(preview));
//                    if (captureRequest != null) {
//                        cameraCaptureSession.setRepeatingRequest(captureRequest, null, cameraHandler);
//                    }
//                } else {
//                    Log.e(TAG, "preview surface is null");
//                }
//            } catch (CameraAccessException | IllegalStateException e) {
//                Log.e(TAG, "Error", e);
//            }
//        }
//    }

    public void closeCamera() {
        closeCamera(true);
    }

    public void closeCamera(boolean resetSurface) {
        resetCameraValues();
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (cameraHandler != null) {
            cameraHandler.getLooper().quitSafely();
            cameraHandler = null;
        }
        if (resetSurface) {
            surface1 = null;
            surface2 = null;
            builderInputSurface = null;
        }
        prepared = false;
        running = false;
    }

    @Override
    public void onOpened(@NonNull CameraDevice cameraDevice) {
        this.cameraDevice = cameraDevice;
        startPreview(cameraDevice);
        semaphore.release();
        Log.i(TAG, "Camera opened");
    }

    @Override
    public void onDisconnected(@NonNull CameraDevice cameraDevice) {
        cameraDevice.close();
        semaphore.release();
        if (cameraCallbacks != null) cameraCallbacks.onCameraDisconnected();
        Log.i(TAG, "Camera disconnected");
    }

    @Override
    public void onError(@NonNull CameraDevice cameraDevice, int i) {
        cameraDevice.close();
        semaphore.release();
        if (cameraCallbacks != null) cameraCallbacks.onCameraError("Open camera failed: " + i);
        Log.e(TAG, "Open failed");
    }

    @Nullable
    private String getCameraIdForFacing(CameraManager cameraManager, CameraHelper.Facing facing)
            throws CameraAccessException {
        int selectedFacing = getFacing(facing);
        for (String cameraId : cameraManager.getCameraIdList()) {
            Integer cameraFacing =
                    cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING);
            if (cameraFacing != null && cameraFacing == selectedFacing) {
                return cameraId;
            }
        }
        return null;
    }

    @Nullable
    private CameraCharacteristics getCharacteristicsForFacing(CameraManager cameraManager,
                                                              CameraHelper.Facing facing) throws CameraAccessException {
        String cameraId = getCameraIdForFacing(cameraManager, facing);
        return cameraId != null ? cameraManager.getCameraCharacteristics(cameraId) : null;
    }

    private static int getFacing(CameraHelper.Facing facing) {
        return facing == CameraHelper.Facing.BACK ? CameraMetadata.LENS_FACING_BACK
                : CameraMetadata.LENS_FACING_FRONT;
    }
}

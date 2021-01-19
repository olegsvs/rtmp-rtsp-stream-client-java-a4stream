package com.pedro.rtplibrary.kuzalex;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.input.gl.render.CameraRender;
import com.pedro.encoder.input.gl.render.RenderHandler;
import com.pedro.encoder.input.gl.render.ScreenRender;
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender;
import com.pedro.encoder.input.gl.render.filters.NoFilterRender;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by pedro on 27/01/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ManagerRender4 {

    //Increase it to render more than 1 filter and set filter by position.
    // You must modify it before create your rtmp or rtsp object.
    public static int numFilters1 = 1;

    //Increase it to render more than 1 filter and set filter by position.
    // You must modify it before create your rtmp or rtsp object.
    public static int numFilters2 = 1;

    private CameraRender cameraRender;
    private List<BaseFilterRender> baseFilterRender1 = new ArrayList<>(numFilters1);
    private List<BaseFilterRender> baseFilterRender2 = new ArrayList<>(numFilters2);
    private ScreenRender screenRender1;
    private ScreenRender screenRender2;

    private int width;
    private int height;
    private int previewWidth;
    private int previewHeight;
    private Context context;

    public ManagerRender4() {
        cameraRender = new CameraRender();
        for (int i = 0; i < numFilters1; i++) baseFilterRender1.add(new NoFilterRender());
        for (int i = 0; i < numFilters2; i++) baseFilterRender2.add(new NoFilterRender());
        screenRender1 = new ScreenRender();
        screenRender2 = new ScreenRender();
    }

    public void initGl(Context context, int encoderWidth, int encoderHeight, int previewWidth,
                       int previewHeight) {
        this.context = context;
        this.width = encoderWidth;
        this.height = encoderHeight;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
        cameraRender.initGl(width, height, context, previewWidth, previewHeight);

        for (int i = 0; i < numFilters1; i++) {
            int textId = i == 0 ? cameraRender.getTexId() : baseFilterRender1.get(i - 1).getTexId();
            baseFilterRender1.get(i).setPreviousTexId(textId);
            baseFilterRender1.get(i).initGl(width, height, context, previewWidth, previewHeight);
            baseFilterRender1.get(i).initFBOLink();
        }
        screenRender1.setStreamSize(encoderWidth, encoderHeight);
        screenRender1.setTexId(baseFilterRender1.get(numFilters1 - 1).getTexId());
        screenRender1.initGl(context);


        for (int i = 0; i < numFilters2; i++) {
            int textId = i == 0 ? cameraRender.getTexId() : baseFilterRender2.get(i - 1).getTexId();
            baseFilterRender2.get(i).setPreviousTexId(textId);
            baseFilterRender2.get(i).initGl(width, height, context, previewWidth, previewHeight);
            baseFilterRender2.get(i).initFBOLink();
        }
        screenRender2.setStreamSize(encoderWidth, encoderHeight);
        screenRender2.setTexId(baseFilterRender2.get(numFilters2 - 1).getTexId());
        screenRender2.initGl(context);


    }

    public void drawOffScreen() {
        cameraRender.draw();
        for (BaseFilterRender baseFilterRender : baseFilterRender1) baseFilterRender.draw();
        for (BaseFilterRender baseFilterRender : baseFilterRender2) baseFilterRender.draw();
    }

    public void drawScreen1(int width, int height, boolean keepAspectRatio, int mode, int rotation,
                           boolean isPreview, boolean flipStreamVertical, boolean flipStreamHorizontal) {
        screenRender1.draw(width, height, keepAspectRatio, mode, rotation, isPreview,
                flipStreamVertical, flipStreamHorizontal);
    }

    public void drawScreen2(int width, int height, boolean keepAspectRatio, int mode, int rotation,
                            boolean isPreview, boolean flipStreamVertical, boolean flipStreamHorizontal) {
        screenRender2.draw(width, height, keepAspectRatio, mode, rotation, isPreview,
                flipStreamVertical, flipStreamHorizontal);
    }

    public void release() {
        cameraRender.release();
        for (int i = 0; i < this.baseFilterRender1.size(); i++) {
            this.baseFilterRender1.get(i).release();
            this.baseFilterRender1.set(i, new NoFilterRender());
        }
        screenRender1.release();

        for (int i = 0; i < this.baseFilterRender2.size(); i++) {
            this.baseFilterRender2.get(i).release();
            this.baseFilterRender2.set(i, new NoFilterRender());
        }
        screenRender2.release();
    }

    public void enableAA1(boolean AAEnabled) {
        screenRender1.setAAEnabled(AAEnabled);
    }
    public boolean isAA1Enabled() {
        return screenRender1.isAAEnabled();
    }
    public void enableAA2(boolean AAEnabled) {
        screenRender2.setAAEnabled(AAEnabled);
    }
    public boolean isAA2Enabled() {
        return screenRender2.isAAEnabled();
    }

    public void updateFrame() {
        cameraRender.updateTexImage();
    }

    public SurfaceTexture getSurfaceTexture() {
        return cameraRender.getSurfaceTexture();
    }

    public Surface getSurface() {
        return cameraRender.getSurface();
    }

    public void setFilter1(int position, BaseFilterRender baseFilterRender) {
        final int id = this.baseFilterRender1.get(position).getPreviousTexId();
        final RenderHandler renderHandler = this.baseFilterRender1.get(position).getRenderHandler();
        this.baseFilterRender1.get(position).release();
        this.baseFilterRender1.set(position, baseFilterRender);
        this.baseFilterRender1.get(position).setPreviousTexId(id);
        this.baseFilterRender1.get(position).initGl(width, height, context, previewWidth, previewHeight);
        this.baseFilterRender1.get(position).setRenderHandler(renderHandler);
    }

    public void setFilter2(int position, BaseFilterRender baseFilterRender) {
        final int id = this.baseFilterRender2.get(position).getPreviousTexId();
        final RenderHandler renderHandler = this.baseFilterRender2.get(position).getRenderHandler();
        this.baseFilterRender2.get(position).release();
        this.baseFilterRender2.set(position, baseFilterRender);
        this.baseFilterRender2.get(position).setPreviousTexId(id);
        this.baseFilterRender2.get(position).initGl(width, height, context, previewWidth, previewHeight);
        this.baseFilterRender2.get(position).setRenderHandler(renderHandler);
    }

    public void setCameraRotation(int rotation) {
        cameraRender.setRotation(rotation);
    }

    public void setCameraFlip(boolean isFlipHorizontal, boolean isFlipVertical) {
        cameraRender.setFlip(isFlipHorizontal, isFlipVertical);
    }

//    public void setPreviewSize(int previewWidth, int previewHeight) {
//        for (int i = 0; i < this.baseFilterRender.size(); i++) {
//            this.baseFilterRender.get(i).setPreviewSize(previewWidth, previewHeight);
//        }
//    }
}

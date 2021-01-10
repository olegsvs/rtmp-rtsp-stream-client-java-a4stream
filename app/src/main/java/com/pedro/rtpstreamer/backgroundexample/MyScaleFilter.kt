package com.pedro.rtpstreamer.backgroundexample

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import androidx.annotation.RequiresApi
//import com.pedro.encoder.R
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.pedro.encoder.R

/**
 * Created by pedro on 9/07/18.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class MyScaleFilter : BaseFilterRender() {
    //rotation matrix
    private val squareVertexDataFilter = floatArrayOf( // X, Y, Z, U, V
            -1f, -1f, 0f, 0f, 0f,  //bottom left
            1f, -1f, 0f, 1f, 0f,  //bottom right
            -1f, 1f, 0f, 0f, 1f,  //top left
            1f, 1f, 0f, 1f, 1f)
    private var program = -1
    private var aPositionHandle = -1
    private var aTextureHandle = -1
    private var uMVPMatrixHandle = -1
    private var uSTMatrixHandle = -1
    private var uSamplerHandle = -1

    private val scaleMatrix = FloatArray(16)

    public fun setScale(x:Float, y:Float){

        Matrix.scaleM(scaleMatrix, 0, x, y, 0.0f)
//        Matrix.setRotateM(scaleMatrix, 0, rotation.toFloat(), 0f, 0f, 1.0f)
        //Translation
        //Matrix.translateM(rotationMatrix, 0, 0f, 0f, 0f);
        // Combine the rotation matrix with the projection and camera view
        Matrix.multiplyMM(MVPMatrix, 0, scaleMatrix, 0, MVPMatrix, 0)

    }


    //Set rotation
    //Translation
    //Matrix.translateM(rotationMatrix, 0, 0f, 0f, 0f);
    // Combine the rotation matrix with the projection and camera view
//    var rotation = 0
//        set(rotation) {
//            field = rotation
//            //Set rotation
//            Matrix.scaleM()
//            Matrix.setRotateM(scaleMatrix, 0, rotation.toFloat(), 0f, 0f, 1.0f)
//            //Translation
//            //Matrix.translateM(rotationMatrix, 0, 0f, 0f, 0f);
//            // Combine the rotation matrix with the projection and camera view
//            Matrix.multiplyMM(MVPMatrix, 0, scaleMatrix, 0, MVPMatrix, 0)
//        }
    override fun initGlFilter(context: Context) {
        val vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex)
        val fragmentShader = GlUtil.getStringFromRaw(context, R.raw.simple_fragment)
        program = GlUtil.createProgram(vertexShader, fragmentShader)
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
        uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler")
    }

    override fun drawFilter() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
                SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex)
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false,
                SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex)
        GLES20.glEnableVertexAttribArray(aTextureHandle)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0)
        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0)
        GLES20.glUniform1i(uSamplerHandle, 4)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)
    }

    override fun release() {
        GLES20.glDeleteProgram(program)
    }

    init {
        squareVertex = ByteBuffer.allocateDirect(squareVertexDataFilter.size * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        squareVertex.put(squareVertexDataFilter).position(0)
        Matrix.setIdentityM(MVPMatrix, 0)
        Matrix.setIdentityM(STMatrix, 0)
        Matrix.setIdentityM(scaleMatrix, 0)
    }
}
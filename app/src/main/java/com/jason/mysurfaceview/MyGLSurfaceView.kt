package com.jason.mysurfaceview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class MyGLSurfaceView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    private val renderer: MyGLRenderer

    init {
        // 创建 OpenGL ES 2.0 上下文
        setEGLContextClientVersion(2)
        renderer = MyGLRenderer(context)
        setRenderer(renderer)

        // 仅在绘制数据发生变化时才绘制视图
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setBitmap(bitmap: Bitmap?) {
        renderer.setBitmap(bitmap)
    }
}

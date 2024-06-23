package com.jason.mysurfaceview

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.WindowManager

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val displayMetrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val screenHeight = displayMetrics.heightPixels
        val newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(screenHeight / 3, MeasureSpec.EXACTLY)

        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec)
    }
}

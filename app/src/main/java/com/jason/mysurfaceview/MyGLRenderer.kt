package com.jason.mysurfaceview

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MyGLRenderer : GLSurfaceView.Renderer {

    private lateinit var line: Line
    private lateinit var square: Square
    private var squarePosition = -1.0f // 初始位置
    private var lastTime: Long = 0

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mVPMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 设置背景颜色
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // 初始化线和正方形对象
        line = Line()
        square = Square()
        lastTime = System.currentTimeMillis()

        // 设置视图矩阵
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, -3f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        // 清除颜色缓冲
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 绘制线
        line.draw()

        // 计算组合矩阵
        Matrix.multiplyMM(mVPMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        // 绘制滑动的正方形
        val currentTime = System.currentTimeMillis()
        val elapsedTime = (currentTime - lastTime) / 1000.0f // 计算已过去的时间（秒）

        squarePosition += elapsedTime // 更新正方形位置
        if (squarePosition > 1.0f) {
            squarePosition = -1.0f // 重置位置
        }
        lastTime = currentTime

        square.draw(squarePosition, mVPMatrix)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()

        // 设置正交投影矩阵
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 7f)
    }

    companion object {
        fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}
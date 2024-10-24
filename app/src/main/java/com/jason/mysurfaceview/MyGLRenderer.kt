package com.jason.mysurfaceview

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MyGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private lateinit var mirror: Mirror  // 镜子
    private lateinit var model: Model    // 模型对象

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mVPMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    private var angleY = 0f

    // 帧缓冲对象相关变量
    private var frameBuffer: Int = 0
    private var frameBufferTexture: Int = 0
    private var depthBuffer: Int = 0

    private var width: Int = 0
    private var height: Int = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 设置背景色为灰色
        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 初始化镜子和模型
        mirror = Mirror()
        model = Model(context, "pinkFox.obj", "pinkFox.mtl")  // 请确保模型文件存在

        // 设置摄像机位置和视图矩阵
        Matrix.setLookAtM(
            viewMatrix, 0,
            0f, 0f, -10f,   // 摄像机位置
            0f, 0f, 0f,     // 观察目标点
            0f, 1f, 0f      // 头顶方向向上
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width
        this.height = height
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()

        // 调整投影矩阵的近平面和远平面
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 50f)

        // 创建帧缓冲对象，用于渲染镜像
        createFrameBuffer(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // 更新旋转角度
        angleY += 0.5f

        // 首先渲染镜像到帧缓冲纹理
        renderReflection()

        // 然后正常渲染场景
        renderScene()
    }

    private fun renderReflection() {
        // 绑定帧缓冲对象
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 设置模型矩阵：反射 + 旋转 + 平移
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.scaleM(modelMatrix, 0, -1f, 1f, 1f)  // X轴反射
        Matrix.rotateM(modelMatrix, 0, angleY, 0f, 1f, 0f)  // 应用与原模型相同的旋转
        Matrix.translateM(modelMatrix, 0, 0f, 0f, -5f)  // 模型位置


        // 计算MVP矩阵
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mVPMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        // 绘制模型的镜像
        model.draw(mVPMatrix)

        // 解除帧缓冲绑定
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun renderScene() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        Matrix.scaleM(modelMatrix, 0, 0.33f, 0.33f, 0.33f)  // 缩小为原来的 1/3

        // 设置模型的模型矩阵：旋转 + 平移
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, angleY, 0f, 1f, 0f)  // 应用旋转
        Matrix.translateM(modelMatrix, 0, 0f, 0f, -5f)  // 模型位置

        // 计算MVP矩阵
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mVPMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        // 绘制模型
        model.draw(mVPMatrix)

        // 设置镜子的模型矩阵：旋转
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, angleY, 0f, 1f, 0f)  // 应用旋转

        // 计算MVP矩阵
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mVPMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        // 设置镜子的纹理为帧缓冲纹理（包含镜像）
        mirror.setTexture(frameBufferTexture)

        // 绘制镜子
        mirror.draw(mVPMatrix)
    }

    private fun createFrameBuffer(width: Int, height: Int) {
        // 生成帧缓冲对象
        val frameBuffers = IntArray(1)
        GLES20.glGenFramebuffers(1, frameBuffers, 0)
        frameBuffer = frameBuffers[0]

        // 生成纹理作为颜色附件
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        frameBufferTexture = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTexture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
        )

        // 生成渲染缓冲对象作为深度附件
        val renderBuffers = IntArray(1)
        GLES20.glGenRenderbuffers(1, renderBuffers, 0)
        depthBuffer = renderBuffers[0]
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthBuffer)
        GLES20.glRenderbufferStorage(
            GLES20.GL_RENDERBUFFER,
            GLES20.GL_DEPTH_COMPONENT16,
            width,
            height
        )

        // 将纹理和渲染缓冲对象附加到帧缓冲
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, frameBufferTexture, 0
        )
        GLES20.glFramebufferRenderbuffer(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_DEPTH_ATTACHMENT,
            GLES20.GL_RENDERBUFFER,
            depthBuffer
        )

        // 检查帧缓冲状态
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer is not complete: $status")
        }

        // 解绑帧缓冲和渲染缓冲
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
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

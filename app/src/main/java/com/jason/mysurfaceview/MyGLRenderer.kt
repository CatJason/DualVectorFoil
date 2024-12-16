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
    private val viewMatrixReflection = FloatArray(16)

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
            0f, 0f, -20f,   // 摄像机位置
            0f, 0f, 0f,     // 观察目标点
            0f, 1f, 0f      // 头顶方向向上
        )

        // 启用面剔除
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
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

        // 设置反射视图矩阵
        Matrix.setLookAtM(
            viewMatrixReflection, 0,
            0f, 0f, 20f,    // 反射后的摄像机位置 (对称反射)
            0f, 0f, 0f,     // 观察目标点
            0f, 1f, 0f      // 头顶方向向上
        )

        // 设置模型矩阵
        Matrix.setIdentityM(modelMatrix, 0)

        // 根据 MIRROR_MODE 应用镜像变换
        if (MIRROR_MODE == MIRROR_MODE_BACKWARD) {
            // 镜子背对用户，沿 Z 轴镜像
            Matrix.scaleM(modelMatrix, 0, 1f, 1f, -1f)
        }

        // 应用整体缩放（包括动态的 MODEL_SCALE）
        Matrix.scaleM(modelMatrix, 0, SCALE_FACTOR * MODEL_SCALE, SCALE_FACTOR * MODEL_SCALE, SCALE_FACTOR * MODEL_SCALE)

        // 应用 y 轴额外缩放
        Matrix.scaleM(modelMatrix, 0, 1f, Y_STRETCH_FACTOR, 1f)  // 仅 y 轴缩放

        // 根据 MIRROR_MODE 和 ROTATION_DIRECTION 调整旋转方向
        val rotationMultiplier = when (ROTATION_DIRECTION) {
            ROTATION_CLOCKWISE -> 1f
            ROTATION_COUNTERCLOCKWISE -> -1f
            else -> 1f
        }

        val mirrorAngleY = when (MIRROR_MODE) {
            MIRROR_MODE_FORWARD -> -angleY * rotationMultiplier
            MIRROR_MODE_BACKWARD -> angleY * rotationMultiplier
            else -> angleY * rotationMultiplier
        }

        Matrix.rotateM(modelMatrix, 0, mirrorAngleY, 0f, 1f, 0f)  // 根据模式和方向旋转

        // 应用平移
        Matrix.translateM(modelMatrix, 0, 0f, 0f, 5f)  // 模型位置反射到 z 轴正方向

        // 计算 MVP 矩阵
        Matrix.multiplyMM(tempMatrix, 0, viewMatrixReflection, 0, modelMatrix, 0)
        Matrix.multiplyMM(mVPMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        // 根据 MIRROR_MODE 调整面剔除
        when (MIRROR_MODE) {
            MIRROR_MODE_FORWARD -> {
                // 正对用户时，剔除前面以显示反射后的正面
                GLES20.glCullFace(GLES20.GL_FRONT)
            }
            MIRROR_MODE_BACKWARD -> {
                // 背对用户时，剔除背面以显示模型的背面
                GLES20.glCullFace(GLES20.GL_BACK)
            }
        }

        // 绘制模型
        model.draw(mVPMatrix)

        // 恢复面剔除设置为默认
        GLES20.glCullFace(GLES20.GL_BACK)

        // 解除帧缓冲绑定
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun renderScene() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 设置模型的模型矩阵：缩放 + 旋转 + 平移
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.scaleM(modelMatrix, 0, 0.33f * MODEL_SCALE, 0.33f * MODEL_SCALE, 0.33f * MODEL_SCALE)  // 缩小为原来的 1/3，并应用 MODEL_SCALE
        Matrix.rotateM(modelMatrix, 0, angleY, 0f, 1f, 0f)   // 应用旋转
        Matrix.translateM(modelMatrix, 0, 0f, 0f, -5f)        // 模型位置

        // 计算 MVP 矩阵
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mVPMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        // 绘制模型
        model.draw(mVPMatrix)

        // 设置镜子的模型矩阵：旋转
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, angleY, 0f, 1f, 0f)  // 应用旋转

        // 计算 MVP 矩阵
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
        // 静态常量定义缩放因子
        private const val SCALE_FACTOR = 0.5f        // 整体缩小为原来的 1/2
        private const val Y_STRETCH_FACTOR = 2.0f    // y 轴拉伸两倍

        // 镜子模式常量
        const val MIRROR_MODE_FORWARD = 0   // 正对用户
        const val MIRROR_MODE_BACKWARD = 1  // 背对用户

        // 镜子旋转方向常量
        const val ROTATION_CLOCKWISE = 0           // 顺时针旋转
        const val ROTATION_COUNTERCLOCKWISE = 1    // 逆时针旋转

        // 当前镜子模式，默认为正对用户
        @Volatile
        var MIRROR_MODE = MIRROR_MODE_FORWARD

        // 当前镜子旋转方向，默认为顺时针
        @Volatile
        var ROTATION_DIRECTION = ROTATION_CLOCKWISE

        // 新增：模型缩放参数，默认为1.0f（原始大小）
        @Volatile
        var MODEL_SCALE = 2.0f

        /**
         * 设置镜子的模式
         * @param mode 0 代表正对用户，1 代表背对用户
         */
        fun setMirrorMode(mode: Int) {
            if (mode == MIRROR_MODE_FORWARD || mode == MIRROR_MODE_BACKWARD) {
                MIRROR_MODE = mode
            }
        }

        /**
         * 设置镜子中模型的旋转方向
         * @param direction 0 代表顺时针，1 代表逆时针
         */
        fun setRotationDirection(direction: Int) {
            if (direction == ROTATION_CLOCKWISE || direction == ROTATION_COUNTERCLOCKWISE) {
                ROTATION_DIRECTION = direction
            }
        }

        /**
         * 设置模型的缩放比例
         * @param scale 缩放比例，必须大于0
         */
        fun setModelScale(scale: Float) {
            if (scale > 0f) {
                MODEL_SCALE = scale
            }
        }

        fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}

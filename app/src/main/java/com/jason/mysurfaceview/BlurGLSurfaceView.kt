package com.jason.mysurfaceview

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 将View转换成Bitmap
 */
fun createBitmapFromView(window: Window, view: View, callBack: (Bitmap?, Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888, true)
        convertLayoutToBitmap(
            window, view, bitmap
        ) { copyResult -> //如果成功
            if (copyResult == PixelCopy.SUCCESS) {
                callBack(bitmap, true)
            } else {
                callBack(null, false)
            }
        }
    } else {
        var bitmap: Bitmap? = null
        //开启view缓存bitmap
        view.isDrawingCacheEnabled = true
        //设置view缓存Bitmap质量
        view.drawingCacheQuality = View.DRAWING_CACHE_QUALITY_HIGH
        //获取缓存的bitmap
        val cache: Bitmap? = view.getDrawingCache()
        if (cache != null && !cache.isRecycled) {
            bitmap = Bitmap.createBitmap(cache)
        }
        //销毁view缓存bitmap
        view.destroyDrawingCache()
        //关闭view缓存bitmap
        view.setDrawingCacheEnabled(false)
        callBack(bitmap, bitmap != null)
    }

}

@RequiresApi(Build.VERSION_CODES.O)
private fun convertLayoutToBitmap(
    window: Window, view: View, dest: Bitmap,
    listener: PixelCopy.OnPixelCopyFinishedListener
) {
    //获取layout的位置
    val location = IntArray(2)
    view.getLocationInWindow(location)
    //请求转换
    PixelCopy.request(
        window,
        Rect(location[0], location[1], location[0] + view.width, location[1] + view.height),
        dest, listener, Handler(Looper.getMainLooper())
    )
}

class BlurViewGroup @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private var glSurfaceView: BlurGLSurfaceView? = null

    init {
        post {
            val window = (context as? Activity)?.window?: return@post
            createBitmapFromView(window, this) { bitmap, success ->
                if(success) {
                    glSurfaceView = BlurGLSurfaceView.build(context, bitmap)
                    addView(glSurfaceView)
                }
            }
        }
    }

    fun onResume() {
        glSurfaceView?.onResume()
    }

    fun onPause() {
        glSurfaceView?.onPause()
    }
}

class BlurGLSurfaceView private constructor(context: Context, attrs: AttributeSet? = null) :
    GLSurfaceView(context, attrs) {

    private val renderer: BlurGLRenderer

    init {
        // 创建 OpenGL ES 2.0 上下文
        setEGLContextClientVersion(2)
        renderer = BlurGLRenderer(context)
        setRenderer(renderer)

        // 仅在绘制数据发生变化时才绘制视图
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setBitmap(
        bitmap: Bitmap?, floatArray: FloatArray
    ) {
        renderer.setBitmap(bitmap, floatArray)
    }

    companion object {
        /**
         * @param bitmap 背景图片纹理
         * @param blurSquareTextureCoords bitmap 的裁剪矩阵
         */
        fun build(
            context: Context,
            bitmap: Bitmap?,
            floatArray: FloatArray = floatArrayOf(
                0.0f, 0.0f,  // top left
                0.0f, 1.0f,   // bottom left
                1.0f, 1.0f,   // bottom right
                1.0f, 0.0f    // top right
            )
        ): BlurGLSurfaceView {
            val blurGLSurfaceView = BlurGLSurfaceView(context).apply {
                setBitmap(bitmap, floatArray)
            }
            return blurGLSurfaceView
        }
    }
}

/**
 * post 动态创建添加, 不支持 xml
 */
private class BlurGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var mBitmap: Bitmap? = null
    private var textureId = 0

    private val blurSquareCoords = floatArrayOf(
        -1.0f, 1.0f, 0.0f,  // top left
        -1.0f, -1.0f, 0.0f,  // bottom left
        1.0f, -1.0f, 0.0f,   // bottom right
        1.0f, 1.0f, 0.0f     // top right
    )

    private val blurSquareBuffer = allocateFloatBuffer(blurSquareCoords)

    private var blurSquareTextureBuffer: FloatBuffer? = null

    private val whiteColor = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)  // 白色

    private var whiteSquareProgram: Int = 0
    private var program: Int = 0
    private var vertexShader: Int = 0
    private var whiteFragmentShader: Int = 0

    private val vertexShaderCode = readShaderFromAssets(context, "blur_vertex_shader_code.vert")
    private val whiteFragmentShaderCode =
        readShaderFromAssets(context, "blur_fragment_shader_code.glsl")

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        whiteFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, whiteFragmentShaderCode)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glLinkProgram(program)

        whiteSquareProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(whiteSquareProgram, vertexShader)
        GLES20.glAttachShader(whiteSquareProgram, whiteFragmentShader)
        GLES20.glLinkProgram(whiteSquareProgram)

        // 确保初始化纹理 ID
        textureId = loadTexture()
    }

    override fun onDrawFrame(gl: GL10?) {
        blurSquareTextureBuffer ?: return

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 使用白色正方形着色器进行绘制
        GLES20.glUseProgram(whiteSquareProgram)
        val positionHandle = GLES20.glGetAttribLocation(whiteSquareProgram, "vPosition")
        GLES20.glVertexAttribPointer(
            positionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            0,
            blurSquareBuffer
        )
        GLES20.glEnableVertexAttribArray(positionHandle)

        val texCoordHandle = GLES20.glGetAttribLocation(whiteSquareProgram, "aTexCoord")
        GLES20.glVertexAttribPointer(
            texCoordHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            blurSquareTextureBuffer
        )
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        // 设置颜色混合
        val colorHandle = GLES20.glGetUniformLocation(whiteSquareProgram, "uColor")
        GLES20.glUniform4fv(colorHandle, 1, whiteColor, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)

        // 如果有纹理，则绘制纹理
        if (textureId != 0) {
            GLES20.glUseProgram(program)
            val positionHandleTex = GLES20.glGetAttribLocation(program, "vPosition")
            GLES20.glVertexAttribPointer(
                positionHandleTex,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                blurSquareBuffer
            )
            GLES20.glEnableVertexAttribArray(positionHandleTex)

            val texCoordHandleTex = GLES20.glGetAttribLocation(program, "aTexCoord")
            GLES20.glVertexAttribPointer(
                texCoordHandleTex,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                blurSquareTextureBuffer
            )
            GLES20.glEnableVertexAttribArray(texCoordHandleTex)

            // 绑定纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            val textureHandleTex = GLES20.glGetUniformLocation(program, "uTexture")
            GLES20.glUniform1i(textureHandleTex, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed")
        }
        return shader
    }

    private fun loadTexture(): Int {
        // 如果 Bitmap 为 null 或者被回收，返回 0
        if (mBitmap == null || mBitmap!!.isRecycled) {
            return 0
        }

        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mBitmap, 0)

        return textureIds[0]
    }

    /**
     * @param bitmap 背景图片纹理
     * @param blurSquareTextureCoords bitmap 的裁剪矩阵
     */
    fun setBitmap(bitmap: Bitmap?, blurSquareTextureCoords: FloatArray) {
        mBitmap = bitmap
        blurSquareTextureBuffer = allocateFloatBuffer(blurSquareTextureCoords)
    }

    private fun allocateFloatBuffer(data: FloatArray): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(data.size * 4)
        buffer.order(ByteOrder.nativeOrder())
        val floatBuffer = buffer.asFloatBuffer()
        floatBuffer.put(data)
        floatBuffer.position(0)
        return floatBuffer
    }
}
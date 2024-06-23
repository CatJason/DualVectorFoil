package com.jason.mysurfaceview

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.jason.mysurfaceview.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var glSurfaceView: MyGLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化 GLSurfaceView
        glSurfaceView = MyGLSurfaceView(this)

        // 将 GLSurfaceView 添加到布局中
        binding.glContainer.addView(glSurfaceView)
    }

    override fun onResume() {
        super.onResume()
        // 恢复 GLSurfaceView
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        // 暂停 GLSurfaceView
        glSurfaceView.onPause()
    }

    /**
     * A native method that is implemented by the 'mysurfaceview' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'mysurfaceview' library on application startup.
        init {
            System.loadLibrary("mysurfaceview")
        }
    }
}
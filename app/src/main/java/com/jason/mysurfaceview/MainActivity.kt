package com.jason.mysurfaceview

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.jason.mysurfaceview.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var glSurfaceView: MyGLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置视图绑定
        Log.d("MainActivity", "设置视图绑定")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化 GLSurfaceView
        Log.d("MainActivity", "初始化 GLSurfaceView")
        glSurfaceView = MyGLSurfaceView(this)

        // 将 GLSurfaceView 添加到布局中
        Log.d("MainActivity", "将 GLSurfaceView 添加到布局中")
        binding.glContainer.addView(glSurfaceView)
    }

    override fun onResume() {
        super.onResume()
        // 恢复 GLSurfaceView
        Log.d("MainActivity", "恢复 GLSurfaceView")
        glSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        // 暂停 GLSurfaceView
        Log.d("MainActivity", "暂停 GLSurfaceView")
        glSurfaceView.onPause()
    }

    companion object {
        init {
            // 加载 Assimp 库
            Log.d("MainActivity", "加载 Assimp 库")
            System.loadLibrary("assimpd")
        }
    }
}

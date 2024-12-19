package com.jason.mysurfaceview

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.annotation.RequiresApi
import com.jason.mysurfaceview.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置视图绑定
        Log.d("MainActivity", "设置视图绑定")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        // 恢复 GLSurfaceView
        Log.d("MainActivity", "恢复 GLSurfaceView")
        binding.blur.onResume()
    }

    override fun onPause() {
        super.onPause()
        // 暂停 GLSurfaceView
        Log.d("MainActivity", "暂停 GLSurfaceView")
        binding.blur.onPause()
    }
}

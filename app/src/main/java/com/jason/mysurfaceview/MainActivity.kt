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
    private lateinit var glSurfaceView: MyGLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置视图绑定
        Log.d("MainActivity", "设置视图绑定")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        glSurfaceView = MyGLSurfaceView(this)
        // 初始化 GLSurfaceView
        Log.d("MainActivity", "初始化 GLSurfaceView")


        // 将 GLSurfaceView 添加到布局中
        Log.d("MainActivity", "将 GLSurfaceView 添加到布局中")

        binding.img.post {
            createBitmapFromView(window, binding.img) { bitmap, success ->
                if(success) {
                    glSurfaceView.setBitmap(bitmap)
                    binding.glContainer.addView(glSurfaceView)
                }
            }
        }
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

    /**
     * 将View转换成Bitmap
     */
    private fun createBitmapFromView(window: Window, view: View, callBack: (Bitmap?, Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888, true)
            convertLayoutToBitmap(
                window, view, bitmap
            ) { copyResult -> //如果成功
                if (copyResult == PixelCopy.SUCCESS) {
                    callBack(bitmap,true)
                }else{
                    callBack(null,false)
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
            callBack(bitmap,bitmap!=null)
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

}

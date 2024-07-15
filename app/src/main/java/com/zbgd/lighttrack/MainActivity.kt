package com.zbgd.lighttrack

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zbgd.lighttrack.databinding.ActivityMainBinding
import java.util.Collections
import kotlin.math.abs
import kotlin.math.log


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val lightTrack: LightTrackNcnn = LightTrackNcnn()

    private lateinit var textureView: AutoFitTextureView
    private lateinit var overlayView: OverlayView
    private lateinit var btnSetTarget: Button
    private lateinit var btnTrack: Button
    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var cameraId: String
    private lateinit var cameraManager: CameraManager
    private lateinit var frameHandler: Handler
    private lateinit var frameRunnable: Runnable
    private var hasTarget: Boolean = false
    private var targetRect: Rect? = null



    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 200
        private const val FRAME_INTERVAL = 500L // Interval to capture frames in
    }


    private val TAG = "LightTrack"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        textureView = findViewById(R.id.textureView)
        overlayView = findViewById(R.id.overlayView)
        btnSetTarget = findViewById(R.id.btnSetTarget)
        btnTrack = findViewById(R.id.btnTrack)
        var r = lightTrack.Init(assets)
        if (r){
            Log.i(TAG, "lightTrack init successfully")
        }else{
            Log.i(TAG, "lightTrack init successfully")
        }


        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList[0]

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
        btnSetTarget.setOnClickListener {
            pauseCameraPreview()
            overlayView.visibility = View.VISIBLE
            stopFrameCapture()
        }

        btnTrack.setOnClickListener {
            targetRect = overlayView.getSelectionRect()
            overlayView.clearSelection()
            if (targetRect != null) {
                var bitmap = textureView.bitmap
                if (bitmap != null && targetRect != null){
                    Log.i(TAG, "bitmap width: ${bitmap.width}, height: ${bitmap.height}")

//                    val obj = LightTrackNcnn.Obj()
//                    obj.x = targetRect!!.left.toFloat()
//                    obj.y = targetRect!!.top.toFloat()
//                    obj.w = (targetRect!!.right-targetRect!!.left).toFloat()
//                    obj.h = (targetRect!!.bottom-targetRect!!.top).toFloat()
//                    lightTrack.SetTemplate(bitmap, obj)
                    hasTarget = lightTrack.setTemplate(bitmap, targetRect!!.left.toFloat(),targetRect!!.top.toFloat(),(targetRect!!.right-targetRect!!.left).toFloat(),(targetRect!!.bottom-targetRect!!.top).toFloat())
                    if (hasTarget){
                        Toast.makeText(this, "设置跟踪目标成功", Toast.LENGTH_SHORT).show()
                    }else{
                        Toast.makeText(this, "设置跟踪目标失败", Toast.LENGTH_SHORT).show()
                    }
                }
                resumeCameraPreview()
            } else {
                Toast.makeText(this, "请选择跟踪目标", Toast.LENGTH_SHORT).show()
            }
        }

        setupFrameHandler()
    }

    private fun setupFrameHandler() {
        frameHandler = Handler()
        frameRunnable = object : Runnable {
            override fun run() {
                captureFrame()
                frameHandler.postDelayed(this, FRAME_INTERVAL)
            }
        }
    }

    private fun stopFrameCapture() {
        frameHandler.removeCallbacks(frameRunnable)
    }



    private fun captureFrame() {
        if (textureView.isAvailable) {
            val bitmap = textureView.bitmap
//            val bitmap = Bitmap.createBitmap(textureView.width, textureView.height, Bitmap.Config.ARGB_8888)

            if (bitmap != null){
//                var selectionRect = overlayView.getSelectionRect()
                if (targetRect != null) {
//                    var result = lightTrack.setTemplate(bitmap, selectionRect.left.toFloat(),selectionRect.top.toFloat(),(selectionRect.right-selectionRect.left).toFloat(),(selectionRect.bottom-selectionRect.top).toFloat())
                    if (hasTarget){
                        var r = lightTrack.Track(bitmap)
                        if (r != null) {
                            Log.i(TAG, "x: ${r.x}, y: ${r.y}, w: ${r.w}, h: ${r.h}")
                            overlayView.setTrackRect(Rect(r.x.toInt(),r.y.toInt(),(r.x+r.w).toInt(),(r.y+r.h).toInt()))
                        }else{
                            overlayView.clearTarget()
                        }
                    }else{
                        Toast.makeText(this, "请框选跟踪目标", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }



    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            return
        }

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startCameraPreview()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun pauseCameraPreview() {
        try {
            cameraCaptureSession.stopRepeating()
        }catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun resumeCameraPreview() {
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
            frameHandler.post(frameRunnable)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }


    private fun startCameraPreview() {
        val surfaceTexture = textureView.surfaceTexture ?: return
//        surfaceTexture.setDefaultBufferSize(textureView.width, textureView.height)
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val previewSizes = map?.getOutputSizes(SurfaceTexture::class.java)

        if (previewSizes.isNullOrEmpty()) {
            Toast.makeText(this, "No supported preview sizes", Toast.LENGTH_SHORT).show()
            return
        }

        // Choose a suitable preview size
        val previewSize = chooseOptimalSize(previewSizes, textureView.width, textureView.height)
        previewSize?.let {
            textureView.setAspectRatio(it.width, it.height)
            surfaceTexture.setDefaultBufferSize(it.width, it.height)
        }


        val surface = Surface(surfaceTexture)

        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)

            cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return

                    cameraCaptureSession = session
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                    cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@MainActivity, "Camera configuration failed", Toast.LENGTH_SHORT).show()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun chooseOptimalSize(choices: Array<Size>, textureViewWidth: Int, textureViewHeight: Int): Size? {
        val aspectRatio = textureViewWidth.toDouble() / textureViewHeight
        Log.i(TAG,"aspectRatio: $aspectRatio")
        var optimalSize: Size? = null
        var minDiff = Double.MAX_VALUE

        for (size in choices) {
            Log.i(TAG,size.toString())
            val sizeAspectRatio = size.width.toDouble() / size.height
//            val sizeAspectRatio = size.height.toDouble() / size.width
            val diff = Math.abs(sizeAspectRatio - aspectRatio)
            if (diff < minDiff) {
                optimalSize = size
                minDiff = diff
            }
        }
        Log.i(TAG,"optimalSize: $optimalSize")
        return optimalSize
    }




    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }



//    private fun loadBitmapFromAssets(context: Context, fileName: String): Bitmap? {
//        val assetManager = context.assets
//        var inputStream: InputStream? = null
//        var bitmap: Bitmap? = null
//        try {
//            inputStream = assetManager.open(fileName)
//            bitmap = BitmapFactory.decodeStream(inputStream)
//        } catch (e: IOException) {
//            e.printStackTrace()
//        } finally {
//            inputStream?.close()
//        }
//        return bitmap
//    }

    override fun onPause() {
        super.onPause()
        cameraDevice.close()
        frameHandler.removeCallbacks(frameRunnable)
    }

    override fun onResume() {
        super.onResume()
        frameHandler.post(frameRunnable)
    }

}
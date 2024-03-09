package com.test.mlfacelandmarkplugin

import android.Manifest
import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.unity3d.player.UnityPlayer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MLFaceLandmarksPlugin : FaceLandmarkerHelper.LandmarkerListener, LifecycleOwner, ActivityLifecycleCallbacks {
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT
    private var unityMessageReceiverName: String = ""

    private lateinit var _context: Context;
    private lateinit var _activity: Activity

    /** Blocking ML operations are performed using this executor */
    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var lifecycleRegistry: LifecycleRegistry;

    private val strBuilder: StringBuilder = StringBuilder()

    private var _isDetecting: Boolean = false

    companion object {
        fun getInstance(receiverName: String): Any {
            val instance = MLFaceLandmarksPlugin()
            instance.init(receiverName)
            return instance
        }
    }

    protected fun finalize() {
        Log.i("MLFL", "MLFaceLandmarksPlugin gets garbage collected")
    }

    fun init(receiverName: String) {
        _activity = UnityPlayer.currentActivity
        _activity.registerActivityLifecycleCallbacks(this)
        _context = _activity.applicationContext
        unityMessageReceiverName = receiverName
        backgroundExecutor = Executors.newSingleThreadExecutor()

        initAnalyzer()

        _activity.runOnUiThread {
            lifecycleRegistry = LifecycleRegistry(this);
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            setUpCamera()
        }
    }

    fun setupDetector(minFaceDetectionConfidence: Float,
                      minFaceTrackingConfidence: Float,
                      minFacePresenceConfidence: Float,
                      inferenceDelegate: Int)
    {

        backgroundExecutor.execute {
            faceLandmarkerHelper?.clearFaceLandmarker();
            faceLandmarkerHelper = FaceLandmarkerHelper(
                context = _context,
                maxNumFaces = 1,
                minFaceDetectionConfidence = minFaceDetectionConfidence,
                minFacePresenceConfidence = minFacePresenceConfidence,
                minFaceTrackingConfidence = minFaceTrackingConfidence,
                currentDelegate = inferenceDelegate,
                faceLandmarkerHelperListener = this
            )
        }
    }

    private fun setUpCamera() {

        Log.i("MLFL", "Setup camera")

        if(ContextCompat.checkSelfPermission(_context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MLFL", "Camera permission denied!")
            return
        }

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(_context)
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()
                // Build and bind the camera use cases
                bindCamera()
            },
            ContextCompat.getMainExecutor(_context)
        )
    }

    private fun bindCamera()
    {
        Log.i("MLFL", "Bind camera")

        if(ContextCompat.checkSelfPermission(_context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MLFL", "Camera permission denied!")
            return
        }

        cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()
        try {
            // Must unbind the use-cases before rebinding them
            cameraProvider?.unbindAll()
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider?.bindToLifecycle(
                this, cameraSelector, imageAnalyzer
            )

        } catch (exc: Exception) {
            Log.e("MLFL", "Use case binding failed", exc)
        }
    }

    private fun unbindCamera()
    {
        Log.i("MLFL", "Unbind camera")
        cameraProvider?.unbindAll()
    }

    private fun initAnalyzer()
    {
        val targetRotation = 0;

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(targetRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor, ::detectFace)
                }
    }

    private fun detectFace(imageProxy: ImageProxy) {
        Log.i("MLFL", "detectFace")

        if(!_isDetecting)
        {
            _isDetecting = true

            faceLandmarkerHelper?.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        }

        imageProxy.close()
    }

    fun resume() {
        if(cameraProvider == null) return
        _activity.runOnUiThread(::bindCamera)
    }

    fun pause() {
        if(cameraProvider == null) return
        _activity.runOnUiThread(::unbindCamera)
    }

    fun dispose() {
        _activity.runOnUiThread {
            faceLandmarkerHelper?.clearFaceLandmarker()
            faceLandmarkerHelper = null
            unbindCamera()
            cameraProvider = null
            camera = null
            imageAnalyzer?.clearAnalyzer()
            imageAnalyzer = null
            backgroundExecutor.shutdown()
            _isDetecting = false
        }
    }

    override fun onError(error: String, errorCode: Int) {
        _isDetecting = false;
        Log.e("MLFL", "($errorCode) $error")
    }

    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle)
    {
        _isDetecting = false;

        if (!resultBundle.result.faceBlendshapes().isPresent) return

        UnityPlayer.UnitySendMessage(unityMessageReceiverName, "MLFLFaceFound", strBuilder.toString())

        strBuilder.clear()
        val list = resultBundle.result.faceBlendshapes().get()[0];

        list.forEach {
                strBuilder.append(it.score())
                strBuilder.append(',')
        }

        Log.i("MLFL", "Inference time: ${resultBundle.inferenceTime} ms")
        UnityPlayer.UnitySendMessage(unityMessageReceiverName, "MLFLBlendshapeResults", strBuilder.toString())
    }

    override fun onEmpty() {
        _isDetecting = false;
        UnityPlayer.UnitySendMessage(unityMessageReceiverName, "MLFLFaceLost", strBuilder.toString())
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry;
    }

    override fun onActivityResumed(p0: Activity) = resume();

    override fun onActivityPaused(p0: Activity) = pause();

    override fun onActivityCreated(p0: Activity, p1: Bundle?) { }

    override fun onActivityStarted(p0: Activity) { }

    override fun onActivityStopped(p0: Activity) { }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) { }

    override fun onActivityDestroyed(p0: Activity) { }
}
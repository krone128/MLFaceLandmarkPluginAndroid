package com.neatyassets.mediapipeFaceLandmarkPlugin

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.github.florent37.application.provider.ActivityProvider
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Field
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.jvm.optionals.getOrDefault


private const val TAG = "MediaPipeFaceLandmarkPlugin"

class MLFaceLandmarksPlugin : FaceLandmarkerHelper.LandmarkerListener, LifecycleOwner, ActivityLifecycleCallbacks {
    private var landmarksBufferAddress: Long = 0
    private var blendshapesBufferAddress: Long = 0
    private var transformationMatricesBufferAddress: Long = 0
    private val landmarkFloatElementsSize = 5
    private val faceDetectorInputShapeWidth = 480
    private val faceDetectorInputShapeHeight = 640

    private val landmarkArrayLength = 478
    private val blendshapeArrayLength = 52
    private val matrixArrayLength = 16

    private var blendshapesBuffer : ByteBuffer? = null
    private var landmarksBuffer : ByteBuffer? = null
    private var transformationMatricesBuffer : ByteBuffer? = null

    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private var managedBridge : ManagedBridge? = null

    private var imageAnalysis: ImageAnalysis? = null
    //private var cameraProvider: ProcessCameraProvider? = null
    private var analyzedImage : ImageProxy? = null

    private lateinit var _context: Context
    private lateinit var _activity: Activity
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var lifecycleRegistry: LifecycleRegistry

    private var _isDetecting: Boolean = false

    private var maxFacesDetectedCount : Int = 1
    private var outputLandmarks : Boolean = false
    private var outputBlendshapes : Boolean = false
    private var outputTransformMatrices : Boolean = false

    companion object {
        fun getInstance(bridge : ManagedBridge): Any {
            val instance = MLFaceLandmarksPlugin()
            instance.init(bridge)
            return instance
        }
    }

    protected fun finalize() {
        Log.i(TAG, "MLFaceLandmarksPlugin gets garbage collected")
    }

    fun init(bridge: ManagedBridge) {
        _activity = ActivityProvider.currentActivity!!
        _activity.registerActivityLifecycleCallbacks(this)
        _context = _activity.applicationContext
        managedBridge = bridge
        backgroundExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(_context)

        _activity.runOnUiThread {
            lifecycleRegistry = LifecycleRegistry(this)
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            setUpCamera()
        }
    }

    fun setupDetector(
        facesCount: Int,
        outputLandmarks : Boolean,
        outputBlendshapes : Boolean,
        outputTransformationMatrices: Boolean,
        minFaceDetectionConfidence: Float,
        minFaceTrackingConfidence: Float,
        minFacePresenceConfidence: Float,
        inferenceDelegate: Int)
    {

        maxFacesDetectedCount = facesCount
        this.outputLandmarks = outputLandmarks
        this.outputBlendshapes = outputBlendshapes
        this.outputTransformMatrices = outputTransformationMatrices

        backgroundExecutor.execute {
            faceLandmarkerHelper?.clearFaceLandmarker()
            faceLandmarkerHelper = FaceLandmarkerHelper(
                context = _context,
                maxNumFaces = facesCount,
                outputBlendshapes = outputBlendshapes,
                outputTransformationMatrices = outputTransformationMatrices,
                minFaceDetectionConfidence = minFaceDetectionConfidence,
                minFacePresenceConfidence = minFacePresenceConfidence,
                minFaceTrackingConfidence = minFaceTrackingConfidence,
                currentDelegate = inferenceDelegate,
                faceLandmarkerHelperListener = this
            )
        }

        initBuffers()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setUpCamera() {

        Log.i(TAG, "Setup camera")

        if(ContextCompat.checkSelfPermission(_context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            PermissionHelper.requestPermission(
                _activity,
                Array(1) { Manifest.permission.CAMERA }) { isGranted ->
                if (!isGranted) {
                    Log.e(TAG, "Camera permission denied!")
                    return@requestPermission
                }
                setUpCamera()
            }
            Log.e(TAG, "Camera permission not granted, trying to request...")
            return
        }

        initAnalyzer()
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun initAnalyzer()
    {
        val targetRotation = Surface.ROTATION_90

        val resSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(faceDetectorInputShapeWidth, faceDetectorInputShapeHeight),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER))
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        val config = ImageAnalysis.Builder()
                .setResolutionSelector(resSelector)
                .setTargetRotation(targetRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)

        Camera2Interop.Extender(config)
            .setCaptureRequestOption(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT)

        imageAnalysis = config.build()
            .also { it.setAnalyzer(backgroundExecutor, ::detectFace) }
    }

    private fun bindCamera()
    {
        Log.i(TAG, "Bind camera")

        if(ContextCompat.checkSelfPermission(_context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            PermissionHelper.requestPermission(
                _activity,
                Array(1) { Manifest.permission.CAMERA }) { isGranted ->
                if (!isGranted) {
                    Log.e(TAG, "Camera permission denied!")
                    return@requestPermission
                }
                resume()
            }
            Log.i(TAG, "Camera permission is not granted, trying to request...")
            return
        }

        val cameraProvider = cameraProviderFuture.get()

        cameraProvider ?: { Log.e(TAG, "Attempt to bind camera while cameraProvider is not initialized") }

        try {
            // Must unbind the use-cases before rebinding them
            cameraProvider?.unbindAll()
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed DEFAULT_FRONT_CAMERA, trying default camera", exc)
            cameraProvider?.bindToLifecycle(this, CameraSelector.Builder().build(), imageAnalysis)
        }
    }

    private fun unbindCamera()
    {
        Log.i(TAG, "Unbind camera")
        val cameraProvider = cameraProviderFuture.get()
        cameraProvider?.unbindAll()
    }

    fun saveBitmapToInternalStorage(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        directoryName: String = "images",
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 100
    ): Boolean {
        // Get the directory for the app's internal storage.
        val directory = context.getDir(directoryName, Context.MODE_PRIVATE)

        // Create the directory if it doesn't exist.
        if (!directory.exists()) {
            directory.mkdir()
        }

        // Create the file.
        val file = File(directory, fileName)

        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(file)
            // Compress the bitmap and write it to the file.
            bitmap.compress(format, quality, fos)
            Log.i(TAG, "Pic saved to:\n ${file.absolutePath}")
            return true
        } catch (e: IOException) {
            Log.e("saveBitmapToInternalStorage", "Error saving bitmap", e)
            return false
        } finally {
            // Close the file output stream.
            fos?.close()
        }
    }

    private fun detectFace(imageProxy: ImageProxy) {
        analyzedImage = imageProxy
        _isDetecting = true

        faceLandmarkerHelper?.detectLiveStream(imageProxy)
    }

    fun resume() {
        cameraProviderFuture.addListener(::bindCamera, ContextCompat.getMainExecutor(_context))
    }

    fun pause() {
        cameraProviderFuture.addListener(::unbindCamera, ContextCompat.getMainExecutor(_context))
    }

    fun dispose() {
        backgroundExecutor.execute {
            faceLandmarkerHelper?.clearFaceLandmarker()
            faceLandmarkerHelper = null
            unbindCamera()
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            _isDetecting = false
            landmarksBuffer = null
            blendshapesBuffer = null
            transformationMatricesBuffer = null
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    fun initBuffers()
    {
        val address: Field = Buffer::class.java.getDeclaredField("address")
        address.isAccessible = true

        landmarksBuffer = ByteBuffer
            .allocateDirect(maxFacesDetectedCount * landmarkArrayLength * landmarkFloatElementsSize * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

        blendshapesBuffer = ByteBuffer.allocateDirect(maxFacesDetectedCount * blendshapeArrayLength * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

        transformationMatricesBuffer = ByteBuffer.allocateDirect(maxFacesDetectedCount * matrixArrayLength * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

        landmarksBufferAddress = address.getLong(landmarksBuffer)
        blendshapesBufferAddress = address.getLong(blendshapesBuffer)
        transformationMatricesBufferAddress = address.getLong(transformationMatricesBuffer)

        managedBridge?.faceDetectionSetup(maxFacesDetectedCount,
            landmarkArrayLength,
            landmarksBufferAddress,
            blendshapeArrayLength,
            blendshapesBufferAddress,
            matrixArrayLength,
            transformationMatricesBufferAddress,)
    }

    override fun onError(error: String, errorCode: Int) {
        analyzedImage?.close()
        _isDetecting = false
        _activity.runOnUiThread { managedBridge?.faceDetectionError(error) }
    }

    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle)
    {
        analyzedImage?.close()
        _isDetecting = false

        val numFaces = resultBundle.result.faceLandmarks().size

        if(numFaces == 0) { return }

        if(outputLandmarks)
        {
            val buffer = landmarksBuffer!!
            val faceLandmarks = resultBundle.result.faceLandmarks()
            buffer.position(0)
            faceLandmarks.forEach { landmarkList ->
                landmarkList.forEach { landmark ->
                    buffer.putFloat(landmark.x())
                    buffer.putFloat(landmark.y())
                    buffer.putFloat(landmark.z())
                    buffer.putFloat(landmark.presence().getOrDefault(0f))
                    buffer.putFloat(landmark.visibility().getOrDefault(0f))
                }
            }
        }

        if (outputBlendshapes && resultBundle.result.faceBlendshapes().isPresent) {
            blendshapesBuffer!!.position(0)
            val faceBlendshapes = resultBundle.result.faceBlendshapes().get()
            faceBlendshapes.forEach{ blendshapeList ->
                blendshapeList.forEach{ category ->
                    blendshapesBuffer!!.putFloat(category.score())
                }
            }
        }

        if (outputTransformMatrices && resultBundle.result.facialTransformationMatrixes().isPresent) {
            transformationMatricesBuffer!!.position(0)
            val matrices = resultBundle.result.facialTransformationMatrixes().get()

            matrices.forEach { matrix ->
                matrix.forEach {
                    transformationMatricesBuffer!!.putFloat(it)
                }
            }
        }

        managedBridge?.faceDetectionResult(numFaces,
            resultBundle.inferenceTime)
    }

    override fun onEmpty() {
        analyzedImage?.close()
        _isDetecting = false
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onActivityResumed(p0: Activity) = resume()

    override fun onActivityPaused(p0: Activity) = pause()

    override fun onActivityCreated(p0: Activity, p1: Bundle?) { }

    override fun onActivityStarted(p0: Activity) { }

    override fun onActivityStopped(p0: Activity) { }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) { }

    override fun onActivityDestroyed(p0: Activity) { }
}
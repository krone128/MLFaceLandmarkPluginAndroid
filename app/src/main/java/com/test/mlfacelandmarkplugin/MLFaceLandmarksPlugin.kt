package com.test.mlfacelandmarkplugin

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.util.Log
import android.util.Range
import android.util.Size
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.jvm.optionals.getOrDefault

class MLFaceLandmarksPlugin : FaceLandmarkerHelper.LandmarkerListener, LifecycleOwner, ActivityLifecycleCallbacks {
    private val landmarkFloatElementsSize = 5
    private val faceDetectorInputShapeWidth = 640
    private val faceDetectorInputShapeHeight = 480

    private var blendshapesBuffer : FloatArray? = null
    private var landmarksBuffer : FloatArray? = null
    private var transformationMatricesBuffer : FloatArray? = null

    private var faceLandmarkerHelper: FaceLandmarkerHelper? = null
    private var managedBridge : ManagedBridge? = null

    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzedImage : ImageProxy? = null

    private lateinit var _context: Context
    private lateinit var _activity: Activity

    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var lifecycleRegistry: LifecycleRegistry

    private var _isDetecting: Boolean = false

    private var maxFacesDetectedCount : Int = 1
    private var outputLandmarks : Boolean = false
    private var outputBlendshapes : Boolean = false;
    private var outputTransformMatrices : Boolean = false;

    companion object {
        fun getInstance(bridge : ManagedBridge): Any {
            val instance = MLFaceLandmarksPlugin()
            instance.init(bridge)
            return instance
        }
    }

    protected fun finalize() {
        Log.i("MLFL", "MLFaceLandmarksPlugin gets garbage collected")
    }

    fun init(bridge: ManagedBridge) {
        _activity = ActivityProvider.currentActivity!!
        _activity.registerActivityLifecycleCallbacks(this)
        _context = _activity.applicationContext
        managedBridge = bridge
        backgroundExecutor = Executors.newSingleThreadExecutor()

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
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setUpCamera() {

        Log.i("MLFL", "Setup camera")

        if(ContextCompat.checkSelfPermission(_context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            PermissionHelper.requestPermission(_activity, Array(1){Manifest.permission.CAMERA}) { isGranted ->
                if (!isGranted) {
                    Log.e("MLFL", "Camera permission denied!")
                    return@requestPermission
                }
                setUpCamera()
            }
            Log.e("MLFL", "Camera permission not granted, trying to request...")
            return
        }

        initAnalyzer()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(_context)

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

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    private fun initAnalyzer()
    {
        val targetRotation = 0

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
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 60))
            .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            .setCaptureRequestOption(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_MOTION_TRACKING)
            .setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)

        imageAnalysis = config.build()
            .also { it.setAnalyzer(backgroundExecutor, ::detectFace) }
    }

    private fun bindCamera()
    {
        Log.i("MLFL", "Bind camera")

        if(ContextCompat.checkSelfPermission(_context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            PermissionHelper.requestPermission(_activity, Array(1){Manifest.permission.CAMERA}) { isGranted ->
                if (!isGranted) {
                    Log.e("MLFL", "Camera permission denied!")
                    return@requestPermission
                }
                bindCamera()
            }
            Log.i("MLFL", "Camera permission is not granted, trying to request...")
            return
        }

        cameraProvider ?: { Log.e("MLFL", "Attempt to bind camera while cameraProvider is not initialized") }

        try {
            // Must unbind the use-cases before rebinding them
            cameraProvider?.unbindAll()
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis)
        } catch (exc: Exception) {
            Log.e("MLFL", "Use case binding failed DEFAULT_FRONT_CAMERA, trying default camera", exc)
            cameraProvider?.bindToLifecycle(this, CameraSelector.Builder().build(), imageAnalysis)
        }
    }

    private fun unbindCamera()
    {
        Log.i("MLFL", "Unbind camera")
        cameraProvider?.unbindAll()
    }

    private fun detectFace(imageProxy: ImageProxy) {
        if(_isDetecting)
        {
            Log.i("MLFL", "detectFace Already detecting")
        }

        analyzedImage = imageProxy
        _isDetecting = true

        faceLandmarkerHelper?.detectLiveStream(imageProxy)
    }

    fun resume() {
        cameraProvider ?: return
        _activity.runOnUiThread(::bindCamera)
    }

    fun pause() {
        cameraProvider ?: return
        _activity.runOnUiThread(::unbindCamera)
    }

    fun dispose() {
        _activity.runOnUiThread {
            faceLandmarkerHelper?.clearFaceLandmarker()
            faceLandmarkerHelper = null
            unbindCamera()
            cameraProvider = null
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            backgroundExecutor.shutdown()
            _isDetecting = false
        }
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

        var blendshapeArrayLength = 0;
        var landmarkArrayLength = 0;
        var transformMatrixArrayLength = 0;

        val numFaces = resultBundle.result.faceLandmarks().size

        if(numFaces == 0)
            managedBridge?.faceDetectionResult(numFaces,
                0,
                0,
                0,
                null,
                null,
                null,
                resultBundle.inferenceTime).also { return }

        if(outputLandmarks)
        {
            var i = 0;
            val faceLandmarks = resultBundle.result.faceLandmarks()
            landmarkArrayLength = faceLandmarks[0].size

            val bufferSize = maxFacesDetectedCount * landmarkArrayLength * landmarkFloatElementsSize
            if (landmarksBuffer?.size != bufferSize) landmarksBuffer = FloatArray(bufferSize)

            landmarksBuffer?.also { buffer ->
                faceLandmarks.forEach { landmarkList ->
                    landmarkList.forEach { landmark ->
                        buffer[i++] = landmark.x()
                        buffer[i++] = landmark.y()
                        buffer[i++] = landmark.z()
                        buffer[i++] = landmark.presence().getOrDefault(0f)
                        buffer[i++] = landmark.visibility().getOrDefault(0f)
                    }
                }
            }
        }

        if (outputBlendshapes && resultBundle.result.faceBlendshapes().isPresent) {
            var i = 0;
            val faceBlendshapes = resultBundle.result.faceBlendshapes().get()
            blendshapeArrayLength = faceBlendshapes[0].size

            val bufferSize = maxFacesDetectedCount * blendshapeArrayLength
            if (blendshapesBuffer?.size != bufferSize) blendshapesBuffer = FloatArray(bufferSize)

            faceBlendshapes.forEach { blendshapeList ->
                blendshapeList.forEach { blendshape ->
                    blendshapesBuffer!![i++] = blendshape.score()
                }
            }
        }

        if (outputTransformMatrices && resultBundle.result.facialTransformationMatrixes().isPresent) {
            var i = 0;
            val matrices = resultBundle.result.facialTransformationMatrixes().get()
            transformMatrixArrayLength = matrices[0].size

            val bufferSize = transformMatrixArrayLength * maxFacesDetectedCount
            if (transformationMatricesBuffer?.size != bufferSize) transformationMatricesBuffer =
                FloatArray(bufferSize)

            matrices.forEach { matrix ->
                matrix.forEach { matrixElement ->
                    transformationMatricesBuffer!![i++] = matrixElement
                }
            }
        }

        managedBridge?.faceDetectionResult(numFaces,
            landmarkArrayLength,
            blendshapeArrayLength,
            transformMatrixArrayLength,
            landmarksBuffer,
            blendshapesBuffer,
            transformationMatricesBuffer,
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
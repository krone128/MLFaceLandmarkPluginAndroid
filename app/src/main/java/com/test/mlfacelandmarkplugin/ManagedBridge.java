package com.test.mlfacelandmarkplugin;

public interface ManagedBridge
{
    void faceDetectionResult(int facesCount,
                             int landmarksArrayLength,
                             int blendshapesArrayLength,
                             int transformationMatricesArrayLength,
                             float[] landmarks,
                             float[] blendshapes,
                             float[] transformationMatrices,
                             long inferenceTime);

    void faceDetectionError(String message);
}

package com.test.mlfacelandmarkplugin;

public interface ManagedBridge
{
    void faceDetectionResult(int facesCount,
                             int landmarksArrayLength,
                             long landmarksArrayAddress,
                             int blendshapesArrayLength,
                             long blendshapesArrayAddress,
                             int transformationMatricesArrayLength,
                             long transformationMatricesArrayAddress,
                             long inferenceTime);

    void faceDetectionError(String message);
}

package com.visionsnap.facetracking;

public interface ManagedBridge
{
    void faceDetectionSetup(int facesCount,
                            int landmarksArrayLength,
                            long landmarksArrayAddress,
                            int blendshapesArrayLength,
                            long blendshapesArrayAddress,
                            int transformationMatricesArrayLength,
                            long transformationMatricesArrayAddress);

    void faceDetectionResult(int facesCount,
                             long inferenceTime);

    void faceDetectionError(String message);
}

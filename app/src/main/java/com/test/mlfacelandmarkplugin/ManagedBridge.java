package com.test.mlfacelandmarkplugin;

public interface ManagedBridge {
    void faceDetectionResult(float[] data, long inferenceTime);

    void faceLost();

    void faceFound();

    void faceDetectionError(String message);
}

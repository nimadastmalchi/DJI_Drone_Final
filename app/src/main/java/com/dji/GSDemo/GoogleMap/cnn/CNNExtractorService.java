package com.dji.GSDemo.GoogleMap.cnn;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.dnn.Net;

public interface CNNExtractorService {

    Net getConvertedNet(String clsModelPath, String tag);

    String getPredictedLabel(Mat inputImage, Net dnnNet, String classesPath, Mat frame);
}
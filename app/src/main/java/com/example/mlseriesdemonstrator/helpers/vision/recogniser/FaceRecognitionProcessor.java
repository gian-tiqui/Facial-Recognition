package com.example.mlseriesdemonstrator.helpers.vision.recogniser;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.text.Editable;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.example.mlseriesdemonstrator.facial_recognition.FaceRecognitionActivity;
import com.example.mlseriesdemonstrator.helpers.vision.FaceGraphic;
import com.example.mlseriesdemonstrator.helpers.vision.GraphicOverlay;
import com.example.mlseriesdemonstrator.helpers.vision.VisionBaseProcessor;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class FaceRecognitionProcessor extends VisionBaseProcessor<List<Face>> {

    static class Person {
        public String name;
        public List<Float> faceVector;

        public Person() {}

        public Person(String name, List<Float> faceVector) {
            this.name = name;
            this.faceVector = faceVector;
        }
    }

    public interface FaceRecognitionCallback {
        void onFaceRecognised(Face face, float probability, String name);
        void onFaceDetected(Face face, Bitmap faceBitmap, float[] vector);
    }

    private static final String TAG = "FaceRecognitionProcessor";

    // Input image size for our facenet model
    private static final int FACENET_INPUT_IMAGE_SIZE = 112;

    private final FaceDetector detector;
    private final Interpreter faceNetModelInterpreter;
    private final ImageProcessor faceNetImageProcessor;
    private final GraphicOverlay graphicOverlay;
    private final FaceRecognitionCallback callback;
    public FaceRecognitionActivity activity;

    List<Person> recognisedFaceList = new ArrayList<>();

    public FaceRecognitionProcessor(Interpreter faceNetModelInterpreter,
                                    GraphicOverlay graphicOverlay,
                                    FaceRecognitionCallback callback) {
        this.callback = callback;
        this.graphicOverlay = graphicOverlay;
        // initialize processors
        this.faceNetModelInterpreter = faceNetModelInterpreter;
        faceNetImageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(
                        FACENET_INPUT_IMAGE_SIZE,
                        FACENET_INPUT_IMAGE_SIZE,
                        ResizeOp.ResizeMethod.BILINEAR
                )).add(new NormalizeOp(0f, 255f))
                .build();

        FaceDetectorOptions faceDetectorOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                // to ensure we don't count and analyse same person again
                .enableTracking()
                .build();
        detector = FaceDetection.getClient(faceDetectorOptions);

        // Comment this code if you do not want to use firebase

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference collectionRef = db.collection("faces");

        updateRecognisedFaceList(collectionRef);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public Task<List<Face>> detectInImage(ImageProxy imageProxy,
                                          Bitmap bitmap,
                                          int rotationDegrees) {

        InputImage inputImage = InputImage.fromMediaImage(
                Objects.requireNonNull(imageProxy.getImage()), rotationDegrees
        );

        // In order to correctly display the face bounds, the orientation of the analyzed
        // image and that of the viewfinder have to match. Which is why the dimensions of
        // the analyzed image are reversed if its rotation information is 90 or 270.

        boolean reverseDimens = rotationDegrees == 90 || rotationDegrees == 270;
        int width;
        int height;

        if (reverseDimens) {
            width = imageProxy.getHeight();
            height =  imageProxy.getWidth();
        } else {
            width = imageProxy.getWidth();
            height = imageProxy.getHeight();
        }
        return detector.process(inputImage)
                .addOnSuccessListener(faces -> {

                    graphicOverlay.clear();

                    for (Face face : faces) {
                        FaceGraphic faceGraphic = new FaceGraphic(
                                graphicOverlay,
                                face,
                                false,
                                width,
                                height
                        );

//                        Log.d(TAG, "face found, id: " + face.getTrackingId());
//
//                            if (activity != null) {
//                                activity.setTestImage(cropToBBox(
//                                        bitmap,
//                                        face.getBoundingBox(),
//                                        rotation
//                                ));
//                            }

                        // now we have a face, so we can use that to analyse age and gender

                        Bitmap faceBitmap = cropToBBox(
                                bitmap,
                                face.getBoundingBox(),
                                rotationDegrees
                        );

                        if (faceBitmap == null) {
                            Log.d("GraphicOverlay", "Face bitmap null");
                            return;
                        }

                        TensorImage tensorImage = TensorImage.fromBitmap(faceBitmap);
                        ByteBuffer faceNetByteBuffer = faceNetImageProcessor
                                .process(tensorImage)
                                .getBuffer();

                        float[][] faceOutputArray = new float[1][192];

                        faceNetModelInterpreter.run(faceNetByteBuffer, faceOutputArray);

                        Log.d(TAG, "output array: " + Arrays.deepToString(faceOutputArray));

                        if (callback != null) {
                            callback.onFaceDetected(face, faceBitmap, faceOutputArray[0]);
                            if (!recognisedFaceList.isEmpty()) {
                                Pair<String, Float> result = findNearestFace(faceOutputArray[0]);
                                // if distance is within confidence
                                if (result.second < 1.0f) {
                                    faceGraphic.name = result.first;
                                    callback.onFaceRecognised(face, result.second, result.first);
                                }
                            }
                        }

                        graphicOverlay.add(faceGraphic);
                    }
                })
                .addOnFailureListener(e -> {
                    // intentionally left empty
                });
    }

    // looks for the nearest vector in the dataset (using L2 norm)
    // and returns the pair <name, distance>
    private Pair<String, Float> findNearestFace(float[] vector) {
        Pair<String, Float> ret = null;
        float minDistance = Float.MAX_VALUE;

        for (Person person : recognisedFaceList) {
            final String name = person.name;
            final List<Float> knownVector = person.faceVector;

            float distance = 0;
            for (int i = 0; i < vector.length; i++) {
                float diff = vector[i] - knownVector.get(i);
                distance += diff * diff;
            }
            distance = (float) Math.sqrt(distance);

            if (distance < minDistance) {
                minDistance = distance;
                ret = new Pair<>(name, distance);
            }
        }

        return ret;
    }


    public void stop() {
        detector.close();
    }

    private Bitmap cropToBBox(Bitmap image, Rect boundingBox, int rotation) {
        int shift = 0;
        if (rotation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            image = Bitmap.createBitmap(
                    image,
                    0,
                    0,
                    image.getWidth(),
                    image.getHeight(),
                    matrix,
                    true
            );
        }
        if (boundingBox.top >= 0 && boundingBox.bottom <= image.getWidth()
                && boundingBox.top + boundingBox.height() <= image.getHeight()
                && boundingBox.left >= 0
                && boundingBox.left + boundingBox.width() <= image.getWidth()) {
            return Bitmap.createBitmap(
                    image,
                    boundingBox.left,
                    boundingBox.top + shift,
                    boundingBox.width(),
                    boundingBox.height()
            );
        } else return null;
    }

    // Register a name against the vector

    public void registerFace(Editable input, float[] tempVector) {

        List<Float> vectorList = new ArrayList<>();
        for (float value : tempVector) {
            vectorList.add(value);
        }

        // Uncomment this line to turn firebase off and comment the lines below

//        recognisedFaceList.add(new Person(input.toString(), vectorList));

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        CollectionReference collectionRef = db.collection("faces");
        Person person = new Person(input.toString(), vectorList);

        collectionRef.add(person)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Document added with ID: " + documentReference.getId());
                    // Handle success case here
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding document", e);
                    // Handle error case here
                });

        // Update the recognisedFaceList
        updateRecognisedFaceList(collectionRef);
    }

    private void updateRecognisedFaceList(CollectionReference collectionRef) {
        collectionRef.get()
                .addOnSuccessListener(
                        queryDocumentSnapshots -> recognisedFaceList = queryDocumentSnapshots
                                .toObjects(Person.class)
                )
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting documents", e);
                    // Handle error case here
                });
    }

}

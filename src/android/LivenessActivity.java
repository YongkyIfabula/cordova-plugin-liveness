package cordova.plugin.liveness;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import cordova.plugin.liveness.LivenessPlugin;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class LivenessActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView tvInstruction;
    private FaceDetector faceDetector;

    // State Machine (Antrean Tugas)
    private ArrayList<String> taskQueue = new ArrayList<>();
    private int currentTaskIndex = 0;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mengambil layout dari resource Cordova
        int layoutResId = getResources().getIdentifier("activity_liveness", "layout", getPackageName());
        setContentView(layoutResId);

        // Menghubungkan ID komponen UI
        int previewViewId = getResources().getIdentifier("previewView", "id", getPackageName());
        int tvInstructionId = getResources().getIdentifier("tvInstruction", "id", getPackageName());
        
        previewView = findViewById(previewViewId);
        tvInstruction = findViewById(tvInstructionId);

        // 1. Baca antrean tugas dari OutSystems
        setupTaskQueue();

        // 2. Siapkan Google ML Kit
        setupMLKit();

        // 3. Nyalakan Kamera
        startCamera();
    }

    private void setupTaskQueue() {
        Intent intent = getIntent();
        if (intent.getBooleanExtra("TASK_NOD", false)) taskQueue.add("NOD");
        if (intent.getBooleanExtra("TASK_SHAKE", false)) taskQueue.add("SHAKE");
        if (intent.getBooleanExtra("TASK_BLINK", false)) taskQueue.add("BLINK");
        if (intent.getBooleanExtra("TASK_SMILE", false)) taskQueue.add("SMILE");

        updateInstructionText();
    }

    private void setupMLKit() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build();
        faceDetector = FaceDetection.getClient(options);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Konfigurasi Layar (Preview)
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Konfigurasi Analisis Gambar untuk ML Kit
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    processImageProxy(imageProxy);
                });

                // Gunakan Kamera Depan
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("Liveness", "Gagal membuka kamera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void processImageProxy(ImageProxy imageProxy) {
        if (isProcessing) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            
            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        for (Face face : faces) {
                            checkLivenessTask(face);
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        }
    }

    private void checkLivenessTask(Face face) {
        if (currentTaskIndex >= taskQueue.size()) return; // Sudah selesai semua

        try {
            // Memanggil rumus kalkulasi dari "Mandor" LivenessPlugin
            JSONObject eval = LivenessPlugin.evaluateFaceLiveness(face);
            
            String currentTask = taskQueue.get(currentTaskIndex);
            boolean isTaskPassed = false;

            switch (currentTask) {
                case "NOD":
                    isTaskPassed = eval.getBoolean("isNodding");
                    break;
                case "SHAKE":
                    isTaskPassed = eval.getBoolean("isShaking");
                    break;
                case "BLINK":
                    isTaskPassed = eval.getBoolean("isBlinking");
                    break;
                case "SMILE":
                    isTaskPassed = eval.getBoolean("isSmiling");
                    break;
            }

            if (isTaskPassed) {
                // Berhasil melewati 1 tugas, lanjut ke antrean berikutnya
                currentTaskIndex++;
                updateInstructionText();
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void updateInstructionText() {
        if (currentTaskIndex >= taskQueue.size()) {
            tvInstruction.setText("Berhasil! Wajah Terverifikasi.");
            tvInstruction.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50")); // Hijau
            finishWithSuccess();
            return;
        }

        String nextTask = taskQueue.get(currentTaskIndex);
        switch (nextTask) {
            case "NOD":   tvInstruction.setText("Ngangguk (Gerakkan Kepala Atas-Bawah)"); break;
            case "SHAKE": tvInstruction.setText("Geleng (Gerakkan Kepala Kiri-Kanan)"); break;
            case "BLINK": tvInstruction.setText("Silakan Kedipkan Mata Anda"); break;
            case "SMILE": tvInstruction.setText("Silakan Tersenyum Lebar"); break;
        }
    }

    private void finishWithSuccess() {
        isProcessing = true; // Hentikan kamera
        Intent resultIntent = new Intent();
        resultIntent.putExtra("isSuccess", true);
        resultIntent.putExtra("message", "Semua pengecekan berhasil dilewati!");
        setResult(RESULT_OK, resultIntent);
        finish(); // Tutup layar kamera ini dan kembali ke OutSystems
    }
}
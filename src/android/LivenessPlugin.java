package cordova.plugin.liveness;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PermissionHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.LivenessActivity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;
// Nanti kita akan butuh Intent untuk membuka layar kamera
import android.content.Intent; 

import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.Face;

public class LivenessPlugin extends CordovaPlugin {

    private static final String TAG = "LivenessPlugin";
    private static final int CAMERA_REQUEST_CODE = 1001;
    public static final int LIVENESS_ACTIVITY_REQUEST = 1002;

    // 2 Action dari OutSystems
    private static final String ACTION_INITIALIZE = "initialize";
    private static final String ACTION_START_LIVENESS = "startLiveness";

    private CallbackContext initCallbackContext;
    private CallbackContext livenessCallbackContext;
    private FaceDetector faceDetector;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        
        // ACTION 1: INITIALIZE
        if (ACTION_INITIALIZE.equals(action)) {
            this.initCallbackContext = callbackContext;
            
            if (!PermissionHelper.hasPermission(this, Manifest.permission.CAMERA)) {
                PermissionHelper.requestPermission(this, CAMERA_REQUEST_CODE, Manifest.permission.CAMERA);
                
                // --- TAMBAHAN WAJIB 1 ---
                // Tahan jembatan JS agar tidak putus saat menunggu pop-up izin
                PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
                r.setKeepCallback(true);
                callbackContext.sendPluginResult(r);
                
            } else {
                initMLKitDetector();
                initCallbackContext.success("INITIALIZED_SUCCESS");
            }
            return true;
        }

        // ACTION 2: START LIVENESS
        if (ACTION_START_LIVENESS.equals(action)) {
            this.livenessCallbackContext = callbackContext;

            boolean checkBlink = args.optBoolean(0, false);
            boolean checkNod   = args.optBoolean(1, false);
            boolean checkShake = args.optBoolean(2, false);
            boolean checkSmile = args.optBoolean(3, false);

            startLivenessCameraFlow(checkBlink, checkNod, checkShake, checkSmile);
            
            // --- TAMBAHAN WAJIB 2 ---
            // Tahan jembatan JS agar tidak putus saat menunggu kamera selesai
            PluginResult r = new PluginResult(PluginResult.Status.NO_RESULT);
            r.setKeepCallback(true);
            callbackContext.sendPluginResult(r);
            
            return true;
        }

        return false;
    }

    /**
     * Menyiapkan Mesin ML Kit di Background
     */
    private void initMLKitDetector() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // Wajib untuk Senyum & Kedip
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build();

        this.faceDetector = FaceDetection.getClient(options);
        Log.d(TAG, "Google ML Kit Face Detector Siap.");
    }

    /**
     * Memulai Layar Kamera (UI) dengan parameter yang diminta
     */
    private void startLivenessCameraFlow(boolean checkBlink, boolean checkNod, boolean checkShake, boolean checkSmile) {
        // 1. Mandor menyiapkan surat tugas untuk Pekerja Lapangan (LivenessActivity)
        Intent intent = new Intent(cordova.getActivity(), LivenessActivity.class);
        
        intent.putExtra("TASK_BLINK", checkBlink);
        intent.putExtra("TASK_NOD", checkNod);
        intent.putExtra("TASK_SHAKE", checkShake);
        intent.putExtra("TASK_SMILE", checkSmile);

        // 2. Buka layar LivenessActivity dan tunggu hasilnya!
        cordova.setActivityResultCallback(this);
        cordova.getActivity().startActivityForResult(intent, LIVENESS_ACTIVITY_REQUEST);
    }
    
    /**
     * Menangkap hasil kembalian ketika LivenessActivity (Kamera) ditutup
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == LIVENESS_ACTIVITY_REQUEST) {
            if (livenessCallbackContext != null) {
                if (resultCode == cordova.getActivity().RESULT_OK && data != null) {
                    try {
                        JSONObject result = new JSONObject();
                        result.put("isSuccess", data.getBooleanExtra("isSuccess", false));
                        result.put("message", data.getStringExtra("message"));
                        
                        // --- UBAH BAGIAN INI ---
                        // Tambahkan .toString() agar OutSystems menerimanya sebagai teks JSON murni
                        livenessCallbackContext.success(result.toString());
                        
                    } catch (JSONException e) {
                        livenessCallbackContext.error("JSON Error saat merangkai respon.");
                    }
                } else {
                    livenessCallbackContext.error("Proses Liveness dibatalkan atau gagal.");
                }
            }
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initMLKitDetector();
                if (initCallbackContext != null) initCallbackContext.success("INITIALIZED_SUCCESS");
            } else {
                if (initCallbackContext != null) initCallbackContext.error("CAMERA_PERMISSION_DENIED");
            }
        }
    }

    /**
     * Fungsi Evaluasi (Nanti dipanggil di dalam Layar Kamera secara Live)
     */
    public static JSONObject evaluateFaceLiveness(Face face) throws JSONException {
        JSONObject data = new JSONObject();

        // 1. Kedip (Blink)
        Float leftEyeOpen = face.getLeftEyeOpenProbability();
        Float rightEyeOpen = face.getRightEyeOpenProbability();
        boolean isBlinking = (leftEyeOpen != null && leftEyeOpen < 0.2f && rightEyeOpen != null && rightEyeOpen < 0.2f);

        // 2. Senyum (Smile) - PENGECEKAN KE 4
        Float smileProb = face.getSmilingProbability();
        boolean isSmiling = (smileProb != null && smileProb > 0.7f); // > 0.7 artinya tersenyum lebar

        // 3. Rotasi (Nod & Shake)
        float headEulerY = face.getHeadEulerAngleY(); // Geleng (Kiri/Kanan)
        float headEulerX = face.getHeadEulerAngleX(); // Ngangguk (Atas/Bawah)

        boolean isShaking = (headEulerY > 20.0f || headEulerY < -20.0f);
        boolean isNodding = (headEulerX > 15.0f || headEulerX < -15.0f);

        data.put("isBlinking", isBlinking);
        data.put("isSmiling", isSmiling);
        data.put("isShaking", isShaking);
        data.put("isNodding", isNodding);

        return data;
    }
}
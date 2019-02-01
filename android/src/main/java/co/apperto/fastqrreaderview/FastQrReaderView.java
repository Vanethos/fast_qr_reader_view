package co.apperto.fastqrreaderview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.widget.TextView;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.platform.PlatformView;

/**
 * FastQrReaderView
 */
public class FastQrReaderView implements MethodCallHandler, PlatformView {

    private static final int CAMERA_REQUEST_ID = 513469796;
    private static final String TAG = "FastQrReaderView";
    private static final SparseIntArray ORIENTATIONS =
            new SparseIntArray() {
                {
                    append(Surface.ROTATION_0, 0);
                    append(Surface.ROTATION_90, 90);
                    append(Surface.ROTATION_180, 180);
                    append(Surface.ROTATION_270, 270);
                }
            };

    private Activity activity;
    private Registrar registrar;
    private Application.ActivityLifecycleCallbacks activityLifecycleCallbacks;
    // The code to run after requesting camera permissions.
    private Runnable cameraPermissionContinuation;
    private boolean requestingPermission;
    private static MethodChannel channel;
    private Context context;
    private EventChannel.EventSink eventSink;
    private boolean scanning;
    private BarcodeView barcodeView;


    // Whether we should ignore process(). This is usually caused by feeding input data faster than
    // the model can handle.
//    private final AtomicBoolean shouldThrottle = new AtomicBoolean(false);


    public FastQrReaderView(Context context, Registrar registrar) {

        this.registrar = registrar;
        this.context = context;
        this.barcodeView = new BarcodeView(context);
        this.activity = registrar.activity();

        Log.d(TAG, "Initializing the view");
        channel = new MethodChannel(registrar.messenger(), "fast_qr_reader_view");

        channel.setMethodCallHandler(this);

        registrar.addRequestPermissionsResultListener(new CameraRequestPermissionsListener());

        this.activityLifecycleCallbacks =
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        if (requestingPermission) {
                            requestingPermission = false;
                            return;
                        }
                        if (activity == FastQrReaderView.this.activity) {
                            startCameraSource();
                        }
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                        if (activity == FastQrReaderView.this.activity) {
                            if (barcodeView != null) {
                                barcodeView.pause();
                            }
                        }
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {
                        if (activity == FastQrReaderView.this.activity) {
                            if (barcodeView != null) {
                                barcodeView.pause();
                            }
                        }
                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {

                    }
                };
    }

    /*
     * Open Settings screens
     */

    public class CameraRequestPermissionsListener
            implements PluginRegistry.RequestPermissionsResultListener {
        @Override
        public boolean onRequestPermissionsResult(int id, String[] permissions, int[] grantResults) {
            if (id == CAMERA_REQUEST_ID) {
                cameraPermissionContinuation.run();
                return true;
            }
            return false;
        }
    }

    @Override
    public void onMethodCall(MethodCall call, final Result result) {
        switch (call.method) {
            case "initialize": {
                ArrayList<String> codeFormats = call.argument("codeFormats");

                close();
                initCamera(result);
                break;
            }
            case "startScanning":
                startScanning(result);
                break;
            case "stopScanning":
                stopScanning(result);
                break;
            case "dispose": {
                dispose();

                if (this.activity != null && this.activityLifecycleCallbacks != null) {
                    this.activity
                            .getApplication()
                            .unregisterActivityLifecycleCallbacks(this.activityLifecycleCallbacks);
                }
                result.success(null);
                break;
            }
            default:
                result.notImplemented();
                break;
        }
    }

    @Override
    public View getView() {
        Log.d("========","Getting the camera");
        return barcodeView;
    }

    @Override
    public void dispose() {
        if (barcodeView != null) {
            barcodeView.pause();
            barcodeView = null;
        }
    }

    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow.
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }


    void startScanning(@NonNull Result result) {
        scanning = true;
        result.success(null);
    }

    void stopScanning(@NonNull Result result) {
        stopScanning();
        result.success(null);
    }

    private void stopScanning() {
        scanning = false;
    }


    /**
     * QR Reader de-abstractatiom
     */

    private void startCameraSource() {
        if (barcodeView != null) {
            barcodeView.resume();
        }
    }

    private void initCamera(final Result result) {
        try {
            if (cameraPermissionContinuation != null && result != null) {
                result.error("cameraPermission", "Camera permission request ongoing", null);
            }
            cameraPermissionContinuation =
                    new Runnable() {
                        @Override
                        public void run() {
                            cameraPermissionContinuation = null;
                            if (!hasCameraPermission()) {
                                result.error(
                                        "cameraPermission", "MediaRecorderCamera permission not granted", null);
                                return;
                            }
                            open(result);
                        }
                    };
            requestingPermission = false;
            if (hasCameraPermission()) {
                cameraPermissionContinuation.run();
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestingPermission = true;
                    registrar
                            .activity()
                            .requestPermissions(
                                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                                    CAMERA_REQUEST_ID);
                }
            }
        } catch (IllegalArgumentException e) {
            result.error("IllegalArgumentException", e.getMessage(), null);
        }
    }

    private void registerCameraEventChannel() {
        new EventChannel(
                registrar.messenger(), "fast_qr_reader_view/cameraEvents" + 0)
                .setStreamHandler(
                        new EventChannel.StreamHandler() {
                            @Override
                            public void onListen(Object arguments, EventChannel.EventSink eventSink) {
                                FastQrReaderView.this.eventSink = eventSink;
                            }

                            @Override
                            public void onCancel(Object arguments) {
                                FastQrReaderView.this.eventSink = null;
                            }
                        });
    }

    private boolean hasCameraPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || activity.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void open(@Nullable final Result result) {
        if (!hasCameraPermission()) {
            if (result != null)
                result.error("cameraPermission", "Camera permission not granted", null);
        } else {

            if (barcodeView == null) {
                barcodeView = new BarcodeView(context);
            }

            barcodeView.decodeContinuous(new BarcodeCallback() {
                @Override
                public void barcodeResult(BarcodeResult result) {
                    Log.w(TAG, "onSuccess: " + result.getText());
                    channel.invokeMethod("updateCode", result.getText());
                    stopScanning();
                }

                @Override
                public void possibleResultPoints(List<ResultPoint> resultPoints) {

                }
            });

            startCameraSource();
            registerCameraEventChannel();

            Map<String, Object> reply = new HashMap<>();
            reply.put("textureId", 0);
            reply.put("previewWidth", barcodeView.getMeasuredWidth());
            reply.put("previewHeight", barcodeView.getMeasuredHeight());
            result.success(reply);
        }
    }

    private void close() {
        if (barcodeView != null) {
            barcodeView.stopDecoding();
        }
    }
}


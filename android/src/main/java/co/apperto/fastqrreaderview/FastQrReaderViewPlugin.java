package co.apperto.fastqrreaderview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
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
import android.view.SurfaceView;
import android.view.View;

import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.apperto.fastqrreaderview.common.CameraSource;
import co.apperto.fastqrreaderview.common.CameraSourcePreview;
import co.apperto.fastqrreaderview.java.barcodescanning.BarcodeScanningProcessor;
import co.apperto.fastqrreaderview.java.barcodescanning.OnCodeScanned;
import co.apperto.fastqrreaderview.zxing.FlutterBarcodeView;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.platform.PlatformView;
import io.flutter.view.FlutterView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

/**
 * FastQrReaderViewPlugin
 */
public class FastQrReaderViewPlugin implements MethodCallHandler, PluginRegistry.RequestPermissionsResultListener, PlatformView {

    public static final int REQUEST_PERMISSION = 47;
    private static final String TAG = "FastQrReaderViewPlugin";
    private final FlutterView view;
    private Activity activity;
    private Registrar registrar;
    private static MethodChannel channel;
    private Result permissionResult;
    private FlutterBarcodeView barcodeView;
    private Application.ActivityLifecycleCallbacks activityLifecycleCallbacks;
    private final FlutterView.SurfaceTextureEntry textureEntry;



    // Whether we should ignore process(). This is usually caused by feeding input data faster than
    // the model can handle.
//    private final AtomicBoolean shouldThrottle = new AtomicBoolean(false);


    private FastQrReaderViewPlugin(Registrar registrar, FlutterView view, Activity activity) {


        SurfaceView a = new SurfaceView(activity);

        this.registrar = registrar;
        this.view = view;
        this.activity = activity;

        textureEntry = view.createSurfaceTexture();

        this.barcodeView = new FlutterBarcodeView(activity, textureEntry.surfaceTexture());

        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                channel.invokeMethod("updateCode", result.getText());
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {

            }
        });

        init();

        this.activityLifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                barcodeView.setDecoderFactory(new DefaultDecoderFactory());
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                init();
            }

            @Override
            public void onActivityPaused(Activity activity) {
                barcodeView.pause();
            }

            @Override
            public void onActivityStopped(Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {

            }
        };

        activity.getApplication().registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
    }

    private void init(){
        barcodeView.resume();
    }

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        channel =
                new MethodChannel(registrar.messenger(), "fast_qr_reader_view");

        FastQrReaderViewPlugin plugin = new FastQrReaderViewPlugin(registrar, registrar.view(), registrar.activity());

        channel.setMethodCallHandler(plugin);
        registrar.addRequestPermissionsResultListener(plugin);
    }

    /*
     * Open Settings screens
     */
    private void openSettings() {
        Activity activity = registrar.activity();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    @Override
    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            // for each permission check if the user granted/denied them
            // you may want to group the rationale in a single dialog,
            // this is just an example
            for (int i = 0, len = permissions.length; i < len; i++) {
                String permission = permissions[i];
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    // user rejected the permission
                    boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale( activity, permission );
                    if (! showRationale) {
                        // user also CHECKED "never ask again"
                        // you can either enable some fall back,
                        // disable features of your app
                        // or open another dialog explaining
                        // again the permission and directing to
                        // the app setting
                        permissionResult.success("dismissedForever");
                    } else {
                        permissionResult.success("denied");
                    }
                } else if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    permissionResult.success("granted");
                } else {
                    permissionResult.success("unknown");
                }
            }
            return true;
        }
        permissionResult.success("unknown");
        return false;
    }

    @Override
    public void onMethodCall(MethodCall call, final Result result) {
        switch (call.method) {
            case "init":
                result.success(null);
                break;
            case "availableCameras":
                String[] cameraNames = new String[]{"zxing"};
                List<Map<String, Object>> cameras = new ArrayList<>();
                for (String cameraName : cameraNames) {
                    HashMap<String, Object> details = new HashMap<>();
//                        Object test = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
//                        Log.d(TAG, "onMethodCall: "+test.toString());
                    details.put("name", cameraName);
                    @SuppressWarnings("ConstantConditions")
                    int lensFacing = 0;
                    switch (lensFacing) {
                        case CameraMetadata.LENS_FACING_FRONT:
                            details.put("lensFacing", "front");
                            break;
                        case CameraMetadata.LENS_FACING_BACK:
                            details.put("lensFacing", "back");
                            break;
                        case CameraMetadata.LENS_FACING_EXTERNAL:
                            details.put("lensFacing", "external");
                            break;
                    }
                    cameras.add(details);
                }
                result.success(cameras);
                break;
            case "initialize": {
                Map<String, Object> reply = new HashMap<>();
                reply.put("textureId", textureEntry.id());
                reply.put("previewWidth", barcodeView.getMeasuredWidth());
                reply.put("previewHeight", barcodeView.getMeasuredHeight());
                result.success(reply);
                break;
            }
            case "startScanning":
                startScanning(result);
                break;
            case "stopScanning":
                stopScanning(result);
                break;
            case "checkPermission":
                String permission;
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    permission = "granted";
                } else {
                    permission = "denied";
                }
                result.success(permission);
                break;
            case "requestPermission":
                this.permissionResult = result;
                Activity activity = registrar.activity();
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION);
                break;
            case "settings":
                //result.success(null);
                openSettings();
            case "dispose": {
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
        return barcodeView;
    }

    @Override
    public void dispose() {

    }

    void startScanning(@NonNull Result result) {
        barcodeView.resume();
        result.success(null);
    }

    void stopScanning(@NonNull Result result) {
        stopScanning();
        result.success(null);
    }

    private void stopScanning() {
        barcodeView.pause();
    }

}


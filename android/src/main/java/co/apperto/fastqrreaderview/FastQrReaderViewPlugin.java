package co.apperto.fastqrreaderview;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

/**
 * Created by goncalopalma on 31/01/2019
 * TODO: Class header comment
 */
public class FastQrReaderViewPlugin implements MethodChannel.MethodCallHandler, PluginRegistry.RequestPermissionsResultListener{
    public static final int REQUEST_PERMISSION = 47;
    private MethodChannel.Result permissionResult;
    private PluginRegistry.Registrar registrar;
    private static MethodChannel channel;
    private CameraManager cameraManager;


    public FastQrReaderViewPlugin(PluginRegistry.Registrar registrar) {
        this.registrar = registrar;
        this.cameraManager = (CameraManager) registrar.activity().getSystemService(Context.CAMERA_SERVICE);

    }

    public static void registerWith(PluginRegistry.Registrar registrar) {

        registrar
                .platformViewRegistry()
                .registerViewFactory("fast_qr_reader_view", new FastQrReaderViewFactory(registrar));

        channel = new MethodChannel(registrar.messenger(), "fast_qr_reader_view");
        channel.setMethodCallHandler(new FastQrReaderViewPlugin(registrar));
    }

    @Override
    public void onMethodCall(MethodCall call, final MethodChannel.Result result) {
        switch (call.method) {
            case "init":
                result.success(null);
                break;
            case "checkPermission":
                String permission;
                if (ContextCompat.checkSelfPermission(registrar.activity(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
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
                break;
            case "initialize":
                result.success(null);
                break;
            case "availableCameras":
                try {
                    String[] cameraNames = cameraManager.getCameraIdList();
                    List<Map<String, Object>> cameras = new ArrayList<>();
                    for (String cameraName : cameraNames) {
                        HashMap<String, Object> details = new HashMap<>();
                        CameraCharacteristics characteristics =
                                cameraManager.getCameraCharacteristics(cameraName);
//                        Object test = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
//                        Log.d(TAG, "onMethodCall: "+test.toString());
                        details.put("name", cameraName);
                        @SuppressWarnings("ConstantConditions")
                        int lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
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
                } catch (CameraAccessException e) {
                    result.error("cameraAccess", e.getMessage(), null);
                }
                break;
        }
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
                    boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(registrar.activity(), permission);
                    if (!showRationale) {
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

    private void openSettings() {
        Activity activity = registrar.activity();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName()));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }
}



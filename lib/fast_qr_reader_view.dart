import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

final MethodChannel _channel = const MethodChannel('fast_qr_reader_view')..invokeMethod('init');



/// Checks the current status of the Camera Permission
///
/// returns: [Future<PermissionStatus>] with the status from the check
Future<PermissionStatus> checkCameraPermission() async {
  try {
    print("checkCameraPermission()");
    var permission = await _channel.invokeMethod('checkPermission') as String;
    print("Permission: $permission");
    return _getPermissionStatus(permission);
  } on PlatformException catch (e) {
    print("Error while permissions");
    return Future.value(PermissionStatus.unknown);
  }
}

/// Requests the camera permission
///
/// returns: [Future<PermissionStatus>] with the status from the request
Future<PermissionStatus> requestCameraPermission() async {
  try {
    var result = await _channel.invokeMethod('requestPermission');
    switch (result) {
      case "denied":
        return PermissionStatus.denied;
      case "dismissedForever":
        return PermissionStatus.dismissedForever;
      case "granted":
        return PermissionStatus.granted;
      default:
        return PermissionStatus.unknown;
    }
  } on PlatformException catch (e) {
    return Future.value(PermissionStatus.unknown);
  }
}

/// Gets the PermissionStatus from the channel Method
///
/// Given a [String] status from the method channel, it returns a
/// [PermissionStatus]
PermissionStatus _getPermissionStatus(String status) {
  switch (status) {
    case "denied":
      return PermissionStatus.denied;
    case "dismissedForever":
      return PermissionStatus.dismissedForever;
    case "granted":
      return PermissionStatus.granted;
    case "restricted":
      return PermissionStatus.restricted;
    default:
      return PermissionStatus.unknown;
  }
}

/// Opens the native settings screen
///
/// Opens the native iOS or Android settings screens for the current app,
/// So that the user can give the app permission even if he has denied them
Future<void> openSettings() {
  try {
    return _channel.invokeMethod('settings');
  } on PlatformException catch (e) {
    return Future.error(e);
  }
}

/// Enum to give us the status of the Permission request/check
enum PermissionStatus {
  granted,

  /// Permission to access the requested feature is denied by the user.
  denied,

  /// The feature is disabled (or not available) on the device.
  disabled,

  /// Permission to access the requested feature is granted by the user.
  dismissedForever,

  /// The user granted restricted access to the requested feature (only on iOS).
  restricted,

  /// Permission is in an unknown state
  unknown
}

class FastQrReaderView {
  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }
}

#import "FastQrReaderViewPlugin.h"
#import <AVFoundation/AVFoundation.h>
#import <libkern/OSAtomic.h>

@interface MyAlertViewDelegate : NSObject<UIAlertViewDelegate>

typedef void (^AlertViewCompletionBlock)(NSInteger buttonIndex);
@property (strong,nonatomic) AlertViewCompletionBlock callback;

+ (void)showAlertView:(UIAlertView *)alertView withCallback:(AlertViewCompletionBlock)callback;

@end

@implementation MyAlertViewDelegate
@synthesize callback;

- (void)alertView:(UIAlertView *)alertView clickedButtonAtIndex:(NSInteger)buttonIndex {
     callback(buttonIndex);
}

+ (void)showAlertView:(UIAlertView *)alertView withCallback:(AlertViewCompletionBlock)callback {
     __block MyAlertViewDelegate *delegate = [[MyAlertViewDelegate alloc] init];
     alertView.delegate = delegate;
     delegate.callback = ^(NSInteger buttonIndex) {
         callback(buttonIndex);
         alertView.delegate = nil;
         delegate = nil;
     };
     [alertView show];
 }

@end

@interface NSError (FlutterError)
@property(readonly, nonatomic) FlutterError *flutterError;
@end

@implementation NSError (FlutterError)
- (FlutterError *)flutterError {
  return [FlutterError errorWithCode:[NSString stringWithFormat:@"Error %d", (int)self.code]
                             message:self.domain
                             details:self.localizedDescription];
}
@end

@interface FastQrReaderViewPlugin ()
@property(readonly, nonatomic) NSObject<FlutterTextureRegistry> *registry;
@property(readonly, nonatomic) NSObject<FlutterBinaryMessenger> *messenger;
@property(readonly, nonatomic) FlutterMethodChannel *channel;
@end

@implementation FastQrReaderViewPlugin

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  FlutterMethodChannel* channel = [FlutterMethodChannel
      methodChannelWithName:@"fast_qr_reader_view"
            binaryMessenger:[registrar messenger]];
    FastQrReaderViewPlugin *instance = [[FastQrReaderViewPlugin alloc] initWithRegistry:[registrar textures] messenger:[registrar messenger] methodChannel: channel];
    
  [registrar addMethodCallDelegate:instance channel:channel];
}

- (instancetype)initWithRegistry:(NSObject<FlutterTextureRegistry> *)registry
                       messenger:(NSObject<FlutterBinaryMessenger> *)messenger
                   methodChannel:(FlutterMethodChannel *)channel {
    self = [super init];
    NSAssert(self, @"super init cannot be nil");
    _registry = registry;
    _messenger = messenger;
    _channel = channel;
    return self;
}

- (void)handleMethodCall:(FlutterMethodCall *)call result:(FlutterResult)result {
    if ([@"checkPermission" isEqualToString:call.method]) {
        AVAuthorizationStatus authStatus = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];
        if (authStatus == AVAuthorizationStatusAuthorized) {
            result(@"granted");
        }
        else if (authStatus == AVAuthorizationStatusDenied) {
            result(@"denied");
        }
        else if (authStatus == AVAuthorizationStatusRestricted) {
            result(@"restricted");
        }
        else if (authStatus == AVAuthorizationStatusNotDetermined) {
            result(@"unknown");
        }
    } else if ([@"settings" isEqualToString:call.method]){
        [[UIApplication sharedApplication] openURL:[NSURL URLWithString:UIApplicationOpenSettingsURLString]];
        result(nil);
    } else if ([@"requestPermission" isEqualToString:call.method]) {
        AVAuthorizationStatus status = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];
        if(status == AVAuthorizationStatusDenied){ // denied
            result(@"alreadyDenied");
        }
        else if (status == AVAuthorizationStatusNotDetermined){ // not determined
            [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:^(BOOL granted) {
                if (granted) {
                    result(@"granted");
                } else {
                    result(@"denied");
                }
            }];
        } else {
            result(@"unknown");
        }
    } else {
        result(@"unknown");
    }
}

@end

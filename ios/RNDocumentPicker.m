#import "RNDocumentPicker.h"

#import <MobileCoreServices/MobileCoreServices.h>

#import <React/RCTConvert.h>
#import <React/RCTBridge.h>
#import <React/RCTUtils.h>
#import "RNCPromiseWrapper.h"
#import "RCTConvert+RNDocumentPicker.h"

static NSString *const E_DOCUMENT_PICKER_CANCELED = @"DOCUMENT_PICKER_CANCELED";
static NSString *const E_INVALID_DATA_RETURNED = @"INVALID_DATA_RETURNED";

static NSString *const OPTION_TYPE = @"type";
static NSString *const OPTION_MULTIPLE = @"allowMultiSelection";
static NSString *const OPTION_COPY_TO = @"copyTo";
static NSString *const OPTION_MAX_NAME_LENGTH = @"maxNameLength";

static NSString *const FIELD_URI = @"uri";
static NSString *const FIELD_FILE_COPY_URI = @"fileCopyUri";
static NSString *const FIELD_COPY_ERR = @"copyError";
static NSString *const FIELD_NAME = @"name";
static NSString *const FIELD_NAME_ENCODED = @"nameEncoded";
static NSString *const FIELD_TYPE = @"type";
static NSString *const FIELD_SIZE = @"size";


@interface RNDocumentPicker () <UIDocumentPickerDelegate, UIAdaptivePresentationControllerDelegate>
@end

@implementation RNDocumentPicker {
    UIDocumentPickerMode mode;
    NSString *copyDestination;
    int maxNameLength;
    RNCPromiseWrapper* promiseWrapper;
    NSMutableArray *urlsInOpenMode;
}

@synthesize bridge = _bridge;

- (instancetype)init
{
    if ((self = [super init])) {
        promiseWrapper = [RNCPromiseWrapper new];
        urlsInOpenMode = [NSMutableArray new];
    }
    return self;
}

- (void)dealloc
{
    for (NSURL *url in urlsInOpenMode) {
        [url stopAccessingSecurityScopedResource];
    }
}

+ (BOOL)requiresMainQueueSetup
{
    return NO;
}

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}

RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(pick:(NSDictionary *)options
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    mode = options[@"mode"] && [options[@"mode"] isEqualToString:@"open"] ? UIDocumentPickerModeOpen : UIDocumentPickerModeImport;
    copyDestination = options[OPTION_COPY_TO];
    maxNameLength = options[OPTION_MAX_NAME_LENGTH] ? [options[OPTION_MAX_NAME_LENGTH] intValue]  : 0;
    UIModalPresentationStyle presentationStyle = [RCTConvert UIModalPresentationStyle:options[@"presentationStyle"]];
    UIModalTransitionStyle transitionStyle = [RCTConvert UIModalTransitionStyle:options[@"transitionStyle"]];
    [promiseWrapper setPromiseWithInProgressCheck:resolve rejecter:reject fromCallSite:@"pick"];

    NSArray *allowedUTIs = [RCTConvert NSArray:options[OPTION_TYPE]];
    UIDocumentPickerViewController *documentPicker = [[UIDocumentPickerViewController alloc] initWithDocumentTypes:allowedUTIs inMode:mode];

    documentPicker.modalPresentationStyle = presentationStyle;
    documentPicker.modalTransitionStyle = transitionStyle;

    documentPicker.delegate = self;
    documentPicker.presentationController.delegate = self;

    documentPicker.allowsMultipleSelection = [RCTConvert BOOL:options[OPTION_MULTIPLE]];

    UIViewController *rootViewController = RCTPresentedViewController();

    [rootViewController presentViewController:documentPicker animated:YES completion:nil];
}


- (void)documentPicker:(UIDocumentPickerViewController *)controller didPickDocumentsAtURLs:(NSArray<NSURL *> *)urls
{
    NSMutableArray *results = [NSMutableArray array];
    for (id url in urls) {
        NSError *error;
        NSMutableDictionary *result = [self getMetadataForUrl:url error:&error];
        if (result) {
            [results addObject:result];
        } else {
            [promiseWrapper reject:E_INVALID_DATA_RETURNED withError:error];
            return;
        }
    }

    [promiseWrapper resolve:results];
}

- (NSMutableDictionary *)getMetadataForUrl:(NSURL *)url error:(NSError **)error
{
    __block NSMutableDictionary *result = [NSMutableDictionary dictionary];

    if (mode == UIDocumentPickerModeOpen) {
        [urlsInOpenMode addObject:url];
    }
    
    // TODO handle error
    [url startAccessingSecurityScopedResource];

    NSFileCoordinator *coordinator = [NSFileCoordinator new];
    NSError *fileError;
    NSString *finalFileName;
    NSString *pathExtension = url.pathExtension;
    NSString *rootFileName =  url.lastPathComponent;
    int pathExtensionLength = (int) [pathExtension length];
    int rootFileNameLength = (int) [rootFileName length];
    
    if (rootFileNameLength > maxNameLength && maxNameLength > 0) {
        int substringToIndex = maxNameLength - pathExtensionLength - 1;
        NSString *substringFileName = [NSString stringWithFormat:@"%@.%@", [rootFileName substringToIndex:substringToIndex], pathExtension];
        finalFileName = substringFileName;
    }
    else {
        finalFileName = rootFileName;
    }
    // TODO double check this implemenation, see eg. https://developer.apple.com/documentation/foundation/nsfilecoordinator/1412420-prepareforreadingitemsaturls
    [coordinator coordinateReadingItemAtURL:url options:NSFileCoordinatorReadingResolvesSymbolicLink error:&fileError byAccessor:^(NSURL *newURL) {
        // If the coordinated operation fails, then the accessor block never runs
        // decoder utf-8
        
        NSString *correctUrl = [((mode == UIDocumentPickerModeOpen) ? url : newURL).absoluteString stringByRemovingPercentEncoding];
        
        NSError *copyError;
        NSString *maybeFileCopyPath;
        
        if ([[pathExtension uppercaseString]  isEqual: @"HEIC"]) {
            NSString *newFileName = [rootFileName stringByReplacingOccurrencesOfString:@".HEIC" withString:@".jpg"];
            NSData *imageData = [NSData dataWithContentsOfURL:url];
            NSString *convertedFilePath = [RNDocumentPicker convertHeicToJpeg: imageData copyToDirectory:copyDestination fileHeicName:newFileName];
            maybeFileCopyPath = convertedFilePath;
            
            correctUrl = [convertedFilePath  stringByRemovingPercentEncoding];
            
            result[FIELD_NAME] = newFileName;
        } else {
            maybeFileCopyPath = copyDestination ? [RNDocumentPicker copyToUniqueDestinationFrom:newURL usingDestinationPreset:copyDestination error:copyError].absoluteString : nil;
            result[FIELD_NAME] = finalFileName;
        }
        if (!copyError) {
            NSString *correctCopyUrl = [maybeFileCopyPath stringByRemovingPercentEncoding];
            result[FIELD_FILE_COPY_URI] = RCTNullIfNil(correctCopyUrl);
            result[FIELD_NAME_ENCODED] = maybeFileCopyPath.lastPathComponent;
        } else {
            result[FIELD_COPY_ERR] = copyError.localizedDescription;
            result[FIELD_FILE_COPY_URI] = [NSNull null];
            result[FIELD_NAME_ENCODED] = [NSNull null];
        }
        
        result[FIELD_URI] = correctUrl;

        NSError *attributesError = nil;
        NSDictionary *fileAttributes = [NSFileManager.defaultManager attributesOfItemAtPath:newURL.path error:&attributesError];
        if(!attributesError) {
            result[FIELD_SIZE] = fileAttributes[NSFileSize];
        } else {
            result[FIELD_SIZE] = [NSNull null];
            NSLog(@"RNDocumentPicker: %@", attributesError);
        }

        if (newURL.pathExtension != nil) {
            CFStringRef extension = (__bridge CFStringRef) newURL.pathExtension;
            CFStringRef uti = UTTypeCreatePreferredIdentifierForTag(kUTTagClassFilenameExtension, extension, NULL);
            CFStringRef mimeType = UTTypeCopyPreferredTagWithClass(uti, kUTTagClassMIMEType);
            if (uti) {
                CFRelease(uti);
            }

            NSString *mimeTypeString = (__bridge_transfer NSString *)mimeType;
            result[FIELD_TYPE] = mimeTypeString;
            if ([[pathExtension uppercaseString]  isEqual: @"HEIC"]) {
                result[FIELD_TYPE] = @"image/jpeg";
            }
        } else {
            result[FIELD_TYPE] = [NSNull null];
        }
    }];

    if (mode != UIDocumentPickerModeOpen) {
        [url stopAccessingSecurityScopedResource];
    }

    if (fileError) {
        *error = fileError;
        return nil;
    } else {
        return result;
    }
}

RCT_EXPORT_METHOD(releaseSecureAccess:(NSArray<NSString *> *)uris
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    NSMutableArray *discardedItems = [NSMutableArray array];
    for (NSString *uri in uris) {
        for (NSURL *url in urlsInOpenMode) {
            if ([url.absoluteString isEqual:uri]) {
                [url stopAccessingSecurityScopedResource];
                [discardedItems addObject:url];
                break;
            }
        }
    }
    [urlsInOpenMode removeObjectsInArray:discardedItems];
    resolve(nil);
}

+ (NSURL *)copyToUniqueDestinationFrom:(NSURL *)url usingDestinationPreset:(NSString *)copyToDirectory error:(NSError *)error
{
    NSURL *destinationRootDir = [self getDirectoryForFileCopy:copyToDirectory];
    // we don't want to rename the file so we put it into a unique location
    NSString *uniqueSubDirName = [[NSUUID UUID] UUIDString];
    NSURL *destinationDir = [destinationRootDir URLByAppendingPathComponent:[NSString stringWithFormat:@"%@/", uniqueSubDirName]];
    NSURL *destinationUrl = [destinationDir URLByAppendingPathComponent:[NSString stringWithFormat:@"%@", url.lastPathComponent]];

    [NSFileManager.defaultManager createDirectoryAtURL:destinationDir withIntermediateDirectories:YES attributes:nil error:&error];
    if (error) {
        return url;
    }
    [NSFileManager.defaultManager copyItemAtURL:url toURL:destinationUrl error:&error];
    if (error) {
        return url;
    } else {
        return destinationUrl;
    }
}

//conver HEIC to JPG

+ (NSString *)convertHeicToJpeg:(NSData*)data copyToDirectory:(NSString *)copyToDirectory fileHeicName: (NSString*)fileHeicName {
    // create temp file
    NSURL *tmpDirFullPath = [self getDirectoryForFileCopy:copyToDirectory];
    NSString *filePath = [[tmpDirFullPath path] stringByAppendingString:[NSString stringWithFormat:@"/%@/", [[NSUUID UUID] UUIDString]]];
    // make dir
    BOOL isDir;
    BOOL exists = [[NSFileManager defaultManager] fileExistsAtPath:filePath isDirectory:&isDir]; if (!exists) {
        [[NSFileManager defaultManager] createDirectoryAtPath: filePath
                                  withIntermediateDirectories:YES attributes:nil error:nil];
    }
    
    NSString *newFilePath = [NSString stringWithFormat:@"%@%@", filePath,fileHeicName];

    // save cropped file
    BOOL status = [data writeToFile:newFilePath atomically:YES];
    if (!status) {
        return nil;
    }

    return newFilePath;
}

+ (NSURL *)getDirectoryForFileCopy:(NSString *)copyToDirectory
{
    if ([@"cachesDirectory" isEqualToString:copyToDirectory]) {
        return [NSFileManager.defaultManager URLsForDirectory:NSCachesDirectory inDomains:NSUserDomainMask].firstObject;
    } else if ([@"documentDirectory" isEqualToString:copyToDirectory]) {
        return [NSFileManager.defaultManager URLsForDirectory:NSDocumentDirectory inDomains:NSUserDomainMask].firstObject;
    }
    // this should not happen as the value is checked in JS, but we fall back to NSTemporaryDirectory()
    return [NSURL fileURLWithPath:NSTemporaryDirectory() isDirectory:YES];
}

- (void)documentPickerWasCancelled:(UIDocumentPickerViewController *)controller
{
    [self rejectAsUserCancellationError];
}

- (void)presentationControllerDidDismiss:(UIPresentationController *)presentationController
{
    [self rejectAsUserCancellationError];
}

- (void)rejectAsUserCancellationError
{
    // TODO make error nullable?
    NSError* error = [NSError errorWithDomain:NSCocoaErrorDomain code:NSUserCancelledError userInfo:nil];
    [promiseWrapper reject:@"user canceled the document picker" withCode:E_DOCUMENT_PICKER_CANCELED withError:error];
}

@end

package com.reactnativedocumentpicker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.GuardedResultAsyncTask;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ReactModule(name = DocumentPickerModule.NAME)
public class DocumentPickerModule extends ReactContextBaseJavaModule {
  public static final String NAME = "RNDocumentPicker";
  private static final int READ_REQUEST_CODE = 41;
  private static final int PICK_DIR_REQUEST_CODE = 42;

  private static final String E_ACTIVITY_DOES_NOT_EXIST = "ACTIVITY_DOES_NOT_EXIST";
  private static final String E_FAILED_TO_SHOW_PICKER = "FAILED_TO_SHOW_PICKER";
  private static final String E_DOCUMENT_PICKER_CANCELED = "DOCUMENT_PICKER_CANCELED";
  private static final String E_UNABLE_TO_OPEN_FILE_TYPE = "UNABLE_TO_OPEN_FILE_TYPE";
  private static final String E_UNKNOWN_ACTIVITY_RESULT = "UNKNOWN_ACTIVITY_RESULT";
  private static final String E_INVALID_DATA_RETURNED = "INVALID_DATA_RETURNED";
  private static final String E_UNEXPECTED_EXCEPTION = "UNEXPECTED_EXCEPTION";

  private static final String OPTION_TYPE = "type";
  private static final String OPTION_MULTIPLE = "allowMultiSelection";
  private static final String OPTION_COPY_TO = "copyTo";
  private static final String OPTION_MAX_NAME_LENGTH = "maxNameLength";

  private static final String FIELD_URI = "uri";
  private static final String FIELD_FILE_COPY_URI = "fileCopyUri";
  private static final String FIELD_COPY_ERROR = "copyError";
  private static final String FIELD_NAME = "name";
  private static final String FIELD_NAME_ENCODED = "nameEncoded";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_SIZE = "size";

  private final ActivityEventListener activityEventListener = new BaseActivityEventListener() {
    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
      final Promise storedPromise = promise;
      if (storedPromise == null) {
        Log.e(NAME, "promise was null in onActivityResult");
        return;
      }
      if (requestCode == READ_REQUEST_CODE) {
        onShowActivityResult(resultCode, data, storedPromise);
      } else if (requestCode == PICK_DIR_REQUEST_CODE) {
        onPickDirectoryResult(resultCode, data);
      }
    }
  };

  private String[] readableArrayToStringArray(ReadableArray readableArray) {
    int size = readableArray.size();
    String[] array = new String[size];
    for (int i = 0; i < size; ++i) {
      array[i] = readableArray.getString(i);
    }
    return array;
  }

  private Promise promise;
  private String copyTo;
  private int maxNameLength;

  public DocumentPickerModule(ReactApplicationContext reactContext) {
    super(reactContext);
    reactContext.addActivityEventListener(activityEventListener);
  }

  @Override
  public void onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy();
    getReactApplicationContext().removeActivityEventListener(activityEventListener);
  }

  @NonNull
  @Override
  public String getName() {
    return NAME;
  }

  @ReactMethod
  public void pick(ReadableMap args, Promise promise) {
    Activity currentActivity = getCurrentActivity();
    this.promise = promise;
    this.copyTo = args.hasKey(OPTION_COPY_TO) ? args.getString(OPTION_COPY_TO) : null;
    this.maxNameLength = args.hasKey(OPTION_MAX_NAME_LENGTH) ? args.getInt(OPTION_MAX_NAME_LENGTH) : 0;
    if (currentActivity == null) {
      sendError(E_ACTIVITY_DOES_NOT_EXIST, "Current activity does not exist");
      return;
    }

    try {
      Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);

      intent.setType("*/*");
      if (!args.isNull(OPTION_TYPE)) {
        ReadableArray types = args.getArray(OPTION_TYPE);
        if (types != null) {
          if (types.size() > 1) {
            String[] mimeTypes = readableArrayToStringArray(types);
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
          } else if (types.size() == 1) {
            intent.setType(types.getString(0));
          }
        }
      }

      boolean multiple = !args.isNull(OPTION_MULTIPLE) && args.getBoolean(OPTION_MULTIPLE);
      intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);

      currentActivity.startActivityForResult(Intent.createChooser(intent, null), READ_REQUEST_CODE, Bundle.EMPTY);
    } catch (ActivityNotFoundException e) {
      sendError(E_UNABLE_TO_OPEN_FILE_TYPE, e.getLocalizedMessage());
    } catch (Exception e) {
      e.printStackTrace();
      sendError(E_FAILED_TO_SHOW_PICKER, e.getLocalizedMessage());
    }
  }

  @ReactMethod
  public void pickDirectory(Promise promise) {
    Activity currentActivity = getCurrentActivity();

    if (currentActivity == null) {
      promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Current activity does not exist");
      return;
    }
    this.promise = promise;
    try {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
      currentActivity.startActivityForResult(intent, PICK_DIR_REQUEST_CODE, null);
    } catch (Exception e) {
      sendError(E_FAILED_TO_SHOW_PICKER, "Failed to create directory picker", e);
    }
  }

  private void onPickDirectoryResult(int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_CANCELED) {
      sendError(E_DOCUMENT_PICKER_CANCELED, "User canceled directory picker");
      return;
    } else if (resultCode != Activity.RESULT_OK) {
      sendError(E_UNKNOWN_ACTIVITY_RESULT, "Unknown activity result: " + resultCode);
      return;
    }

    if (data == null || data.getData() == null) {
      sendError(E_INVALID_DATA_RETURNED, "Invalid data returned by intent");
      return;
    }
    Uri uri = data.getData();

    WritableMap map = Arguments.createMap();
    map.putString(FIELD_URI, uri.toString());
    promise.resolve(map);
  }

  public void onShowActivityResult(int resultCode, Intent data, Promise promise) {
    if (resultCode == Activity.RESULT_CANCELED) {
      sendError(E_DOCUMENT_PICKER_CANCELED, "User canceled document picker");
    } else if (resultCode == Activity.RESULT_OK) {
      Uri uri = null;
      ClipData clipData = null;

      if (data != null) {
        uri = data.getData();
        clipData = data.getClipData();
      }

      try {
        List<Uri> uris = new ArrayList<>();
        // condition order seems to matter: https://github.com/rnmods/react-native-document-picker/issues/317#issuecomment-645222635
        if (clipData != null && clipData.getItemCount() > 0) {
          final int length = clipData.getItemCount();
          for (int i = 0; i < length; ++i) {
            ClipData.Item item = clipData.getItemAt(i);
            uris.add(item.getUri());
          }
        } else if (uri != null) {
          uris.add(uri);
        } else {
          sendError(E_INVALID_DATA_RETURNED, "Invalid data returned by intent");
          return;
        }

        new ProcessDataTask(getReactApplicationContext(), uris, copyTo, maxNameLength, promise).execute();
      } catch (Exception e) {
        sendError(E_UNEXPECTED_EXCEPTION, e.getLocalizedMessage(), e);
      }
    } else {
      sendError(E_UNKNOWN_ACTIVITY_RESULT, "Unknown activity result: " + resultCode);
    }
  }

  private static class ProcessDataTask extends GuardedResultAsyncTask<ReadableArray> {
    private final WeakReference<Context> weakContext;
    private final List<Uri> uris;
    private final String copyTo;
    private final int maxNameLength;
    private final Promise promise;

    protected ProcessDataTask(ReactContext reactContext, List<Uri> uris, String copyTo, int maxNameLength, Promise promise) {
      super(reactContext.getExceptionHandler());
      this.weakContext = new WeakReference<>(reactContext.getApplicationContext());
      this.uris = uris;
      this.copyTo = copyTo;
      this.maxNameLength = maxNameLength;
      this.promise = promise;
    }

    @Override
    protected ReadableArray doInBackgroundGuarded() {
      WritableArray results = Arguments.createArray();
      for (Uri uri : uris) {
        results.pushMap(getMetadata(uri));
      }
      return results;
    }

    @Override
    protected void onPostExecuteGuarded(ReadableArray readableArray) {
      promise.resolve(readableArray);
    }

    private WritableMap getMetadata(Uri uri) {
      Context context = weakContext.get();
      if (context == null) {
        return Arguments.createMap();
      }
      ContentResolver contentResolver = context.getContentResolver();
      String fileType = contentResolver.getType(uri);
      WritableMap map = Arguments.createMap();
      map.putString(FIELD_URI, uri.toString());
      map.putString(FIELD_TYPE, fileType);
      String lastPathFileType = "."+fileType.substring(fileType.lastIndexOf("/") + 1);
      try (Cursor cursor = contentResolver.query(uri, null, null, null, null, null)) {
        if (cursor != null && cursor.moveToFirst()) {
          int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
          if (!cursor.isNull(displayNameIndex)) {
            String finalFileName;
            String rootFileName = cursor.getString(displayNameIndex);
            Boolean isHasMimeType = rootFileName.lastIndexOf(".") != -1;
            String rootMimeType =  isHasMimeType ? rootFileName.substring(rootFileName.lastIndexOf(".")): lastPathFileType;
            int rootFileNameLength = rootFileName.length();
            int rootMimeTypeLength = rootMimeType.length();
            if (this.maxNameLength > 0 && rootFileNameLength > this.maxNameLength ) {
              finalFileName = rootFileName.substring(0, this.maxNameLength - rootMimeTypeLength - 1) + rootMimeType;
            } else {
              finalFileName = isHasMimeType ? rootFileName : rootFileName + rootMimeType;
            }
            String fileNameEncoder = URLEncoder.encode(finalFileName, "utf-8");

            map.putString(FIELD_NAME, finalFileName);
            map.putString(FIELD_NAME_ENCODED, fileNameEncoder);
          }
          int mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
          if (!cursor.isNull(mimeIndex)) {
            map.putString(FIELD_TYPE, cursor.getString(mimeIndex));
          }
          int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
          if (!cursor.isNull(sizeIndex)) {
            map.putInt(FIELD_SIZE, cursor.getInt(sizeIndex));
          }
        }
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      }

      prepareFileUri(context, map, uri);
      return map;
    }

    private void prepareFileUri(Context context, WritableMap map, Uri uri) {
      if (copyTo != null) {
        File dir = context.getCacheDir();
        if (copyTo.equals("documentDirectory")) {
          dir = context.getFilesDir();
        }
        // we don't want to rename the file so we put it into a unique location
        dir = new File(dir, UUID.randomUUID().toString());
        try {
          boolean didCreateDir = dir.mkdir();
          if (!didCreateDir) {
            throw new IOException("failed to create directory at " + dir.getAbsolutePath());
          }
          String fileName = map.getString(FIELD_NAME_ENCODED);
          if (fileName == null) {
            fileName = String.valueOf(System.currentTimeMillis());
          }
          File destFile = new File(dir, fileName);
          String copyPath = copyFile(context, uri, destFile);
          map.putString(FIELD_FILE_COPY_URI, copyPath);
        } catch (Exception e) {
          e.printStackTrace();
          map.putNull(FIELD_FILE_COPY_URI);
          map.putString(FIELD_COPY_ERROR, e.getLocalizedMessage());
        }
      } else {
        map.putNull(FIELD_FILE_COPY_URI);
      }
    }

    public static String copyFile(Context context, Uri uri, File destFile) throws IOException {
      InputStream in = null;
      FileOutputStream out = null;
      try {
        in = context.getContentResolver().openInputStream(uri);
        if (in != null) {
          out = new FileOutputStream(destFile);
          byte[] buffer = new byte[1024];
          int len;
          while ((len = in.read(buffer)) > 0) {
            out.write(buffer, 0, len);
          }
          out.close();
          in.close();
          return destFile.toURI().toString();
        } else {
          throw new NullPointerException("Invalid input stream");
        }
      } catch (Exception e) {
        try {
          if (in != null) {
            in.close();
          }
          if (out != null) {
            out.close();
          }
        } catch (IOException ignored) {}
        throw e;
      }
    }
  }

  private void sendError(String code, String message) {
    sendError(code, message, null);
  }

  private void sendError(String code, String message, Exception e) {
    Promise temp = this.promise;
    if (temp != null) {
      this.promise = null;
      temp.reject(code, message, e);
    }
  }
}

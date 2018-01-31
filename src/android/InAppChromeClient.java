/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/
package com.initialxy.cordova.themeablebrowser;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.JsPromptResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.GeolocationPermissions.Callback;
import android.widget.ProgressBar;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class InAppChromeClient extends WebChromeClient {

    public final int FILECHOOSER_REQUESTCODE=90;
    private CordovaWebView webView;
    private ProgressBar progressBar;
    private String LOG_TAG = "InAppChromeClient";
    private long MAX_QUOTA = 100 * 1024 * 1024;

    private CordovaPlugin cordovaPlugin;
    /**
     * 保持文件选择中，如果选择相机拍摄，保存的Uri
     */
    private Uri takePictureUri;

    public InAppChromeClient(CordovaWebView webView, ProgressBar progressBar, CordovaPlugin cordova) {
        super();
        this.webView = webView;
        this.progressBar=progressBar;
        this.cordovaPlugin=cordova;
    }
    /**
     * Handle database quota exceeded notification.
     *
     * @param url
     * @param databaseIdentifier
     * @param currentQuota
     * @param estimatedSize
     * @param totalUsedQuota
     * @param quotaUpdater
     */
    @Override
    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize,
                                        long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater)
    {
        LOG.d(LOG_TAG, "onExceededDatabaseQuota estimatedSize: %d  currentQuota: %d  totalUsedQuota: %d", estimatedSize, currentQuota, totalUsedQuota);
        quotaUpdater.updateQuota(MAX_QUOTA);
    }

    /**
     * Instructs the client to show a prompt to ask the user to set the Geolocation permission state for the specified origin.
     *
     * @param origin
     * @param callback
     */
    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, Callback callback) {
        super.onGeolocationPermissionsShowPrompt(origin, callback);
        callback.invoke(origin, true, false);
    }

    /**
     * Tell the client to display a prompt dialog to the user.
     * If the client returns true, WebView will assume that the client will
     * handle the prompt dialog and call the appropriate JsPromptResult method.
     *
     * The prompt bridge provided for the ThemeableBrowser is capable of executing any
     * oustanding callback belonging to the ThemeableBrowser plugin. Care has been
     * taken that other callbacks cannot be triggered, and that no other code
     * execution is possible.
     *
     * To trigger the bridge, the prompt default value should be of the form:
     *
     * gap-iab://<callbackId>
     *
     * where <callbackId> is the string id of the callback to trigger (something
     * like "ThemeableBrowser0123456789")
     *
     * If present, the prompt message is expected to be a JSON-encoded value to
     * pass to the callback. A JSON_EXCEPTION is returned if the JSON is invalid.
     *
     * @param view
     * @param url
     * @param message
     * @param defaultValue
     * @param result
     */
    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        // See if the prompt string uses the 'gap-iab' protocol. If so, the remainder should be the id of a callback to execute.
        if (defaultValue != null && defaultValue.startsWith("gap")) {
            if(defaultValue.startsWith("gap-iab://")) {
                PluginResult scriptResult;
                String scriptCallbackId = defaultValue.substring(10);
                if (scriptCallbackId.startsWith("ThemeableBrowser")) {
                    if(message == null || message.length() == 0) {
                        scriptResult = new PluginResult(PluginResult.Status.OK, new JSONArray());
                    } else {
                        try {
                            scriptResult = new PluginResult(PluginResult.Status.OK, new JSONArray(message));
                        } catch(JSONException e) {
                            scriptResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
                        }
                    }
                    this.webView.sendPluginResult(scriptResult, scriptCallbackId);
                    result.confirm("");
                    return true;
                }
            }
            else
            {
                // Anything else with a gap: prefix should get this message
                LOG.w(LOG_TAG, "ThemeableBrowser does not support Cordova API calls: " + url + " " + defaultValue);
                result.cancel();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        if(this.progressBar!=null) {
            if (newProgress >= 100) {
                this.progressBar.setVisibility(View.INVISIBLE);
            } else {
                this.progressBar.setVisibility(View.VISIBLE);
                this.progressBar.setProgress(newProgress);
            }
        }
    }

    // <input type=file> support:
    // openFileChooser() is for pre KitKat and in KitKat mr1 (it's known broken in KitKat).
    // For Lollipop, we use onShowFileChooser().
    public void openFileChooser(ValueCallback<Uri> uploadMsg) {
        this.openFileChooser(uploadMsg, "*/*");
    }

    public void openFileChooser( ValueCallback<Uri> uploadMsg, String acceptType ) {
        this.openFileChooser(uploadMsg, acceptType, null);
    }

    public void openFileChooser(final ValueCallback<Uri> uploadMsg, String acceptType, String capture)
    {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(acceptType);
            cordovaPlugin.cordova.startActivityForResult(new CordovaPlugin() {
                @Override
                public void onActivityResult(int requestCode, int resultCode, Intent intent) {
                    Uri result = intent == null || resultCode != Activity.RESULT_OK ? null : intent.getData();
                  if(result!=null) {
                      String path = getImagePath(result, null);
                      result = Uri.fromFile(new File(path));
                      LOG.d(LOG_TAG, "Receive file chooser URL: " + result);
                  }
                    uploadMsg.onReceiveValue(result);
                }
            }, intent, FILECHOOSER_REQUESTCODE);

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onShowFileChooser(WebView webView, final ValueCallback<Uri[]> filePathsCallback, final WebChromeClient.FileChooserParams fileChooserParams) {
        Intent intent = fileChooserParams.createIntent();
        if(intent.getType().equals("image/*"))intent=getPickImageChooserIntent();
        try {
            cordovaPlugin.cordova.startActivityForResult(new CordovaPlugin() {
                @Override
                public void onActivityResult(int requestCode, int resultCode, Intent intent) {
                    Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, intent);
                    if (result == null&&resultCode==Activity.RESULT_OK) {
                        result = new Uri[]{takePictureUri};
                    }
                    LOG.d(LOG_TAG, "Receive file chooser URL: " + result);
                    filePathsCallback.onReceiveValue(result);
                }
            }, intent, FILECHOOSER_REQUESTCODE);
        } catch (ActivityNotFoundException e) {
            filePathsCallback.onReceiveValue(null);
        }
        return true;
    }



    /**
     * 支持JS调用 window.close()
     * @param window
     */
    @Override
    public void onCloseWindow(WebView window) {
        super.onCloseWindow(window);
        ((ThemeableBrowser)cordovaPlugin).closeDialog();
    }

    /**
     *
     * @return
     */
    public Intent getPickImageChooserIntent() {

        // Determine Uri of camera image to save.
//        Uri outputFileUri = Uri.

        List<Intent> allIntents = new ArrayList();
        PackageManager packageManager = cordovaPlugin.cordova.getActivity().getPackageManager();
        Uri outputFileUri= null;
        try {
            outputFileUri = Uri.fromFile(createImageFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
        // collect all camera intents
        Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
        for (ResolveInfo res : listCam) {
            Intent intent = new Intent(captureIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(res.activityInfo.packageName);
            if (outputFileUri != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
                takePictureUri=outputFileUri;
            }
            allIntents.add(intent);
        }

        // collect all gallery intents
        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        List<ResolveInfo> listGallery = packageManager.queryIntentActivities(galleryIntent, 0);
        for (ResolveInfo res : listGallery) {
            Intent intent = new Intent(galleryIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(res.activityInfo.packageName);
            allIntents.add(intent);
        }

        // the main intent is the last in the list (fucking android) so pickup the useless one
        Intent mainIntent =  allIntents.get(allIntents.size() - 1);
        for (Intent intent : allIntents) {
            if (intent.getComponent().getClassName().equals("com.android.documentsui.DocumentsActivity")) {
                mainIntent = intent;
                break;
            }
        }
        allIntents.remove(mainIntent);

        // Create a chooser from the main intent
        Intent chooserIntent = Intent.createChooser(mainIntent, "Select source");

        // Add all other intents
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, allIntents.toArray(new Parcelable[allIntents.size()]));

        return chooserIntent;
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_" ;
        File storageDir = cordovaPlugin.cordova.getActivity().getExternalCacheDir();
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return image;
    }

    /**
     *
     * @param uri
     * @param selection
     * @return
     */
    private String getImagePath(Uri uri, String selection) {
        if(uri==null)return null;
        String path = null;
        Cursor cursor =cordovaPlugin.cordova.getActivity().getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }

            cursor.close();
        }
        return path;
    }
}

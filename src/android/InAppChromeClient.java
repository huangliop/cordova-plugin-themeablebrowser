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

import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.webkit.JsPromptResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.GeolocationPermissions.Callback;
import android.widget.ProgressBar;

public class InAppChromeClient extends WebChromeClient {

    public final int FILECHOOSER_REQUESTCODE=90;
    private CordovaWebView webView;
    private ProgressBar progressBar;
    private String LOG_TAG = "InAppChromeClient";
    private long MAX_QUOTA = 100 * 1024 * 1024;
    /**
     * 选中后的回调
     */
    private ValueCallback<Uri[]> uploadMessageAbovel;
    private ValueCallback<Uri> uploadMessage;
    private CordovaPlugin cordovaPlugin;

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

    //For Android  >= 4.1
    protected void openFileChooser(ValueCallback<Uri> valueCallback, String acceptType, String capture) {
        this.uploadMessage=valueCallback;
        this.openImageChoes();
    }

    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        this.uploadMessageAbovel=filePathCallback;
        this.openImageChoes();
        return true;
    }
    protected void openFileChooser(ValueCallback<Uri> uploadMsg)
    {
        this.uploadMessage=uploadMsg;
        this.openImageChoes();
    }
    private void openImageChoes(){
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        cordovaPlugin.cordova.startActivityForResult(cordovaPlugin,i,FILECHOOSER_REQUESTCODE);
    }

    /**
     * 收到图片成功回调
     */
    public void onReceiveeValue(int requestCode,int resultCode,Intent intent){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            if (requestCode == FILECHOOSER_REQUESTCODE){
                if (uploadMessageAbovel == null)
                    return;
                uploadMessageAbovel.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
                uploadMessageAbovel = null;
            }
        }
        else if (requestCode == FILECHOOSER_REQUESTCODE){
            if (null == uploadMessage) return;
            Uri result = intent == null || resultCode != cordovaPlugin.cordova.getActivity().RESULT_OK ? null : intent.getData();
            uploadMessage.onReceiveValue(result);
            uploadMessage = null;
        }
    }
}

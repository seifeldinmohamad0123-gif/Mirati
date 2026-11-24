package com.mirati.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_FILE = 4107;
    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private boolean exitDialogShowing = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.rgb(247, 248, 252));
        window.setNavigationBarColor(android.graphics.Color.WHITE);
        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );

        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.rgb(247, 248, 252));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setDatabaseEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"application/json", "text/json", "application/octet-stream"});
                try {
                    startActivityForResult(intent, REQUEST_IMPORT_FILE);
                } catch (Exception e) {
                    fileChooserCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });
        webView.addJavascriptInterface(new MiratiBridge(), "MiratiBridge");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class MiratiBridge {
        @JavascriptInterface
        public boolean saveBackup(String fileName, String content) {
            try {
                String safeName = (fileName == null || fileName.trim().isEmpty())
                    ? "mirati-backup.json" : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/Mirati");
                    values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) return false;
                    try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                        if (output == null) return false;
                        output.write(bytes);
                        output.flush();
                    }
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(uri, ready, null, null);
                    return true;
                } else {
                    File base = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (base == null) return false;
                    File folder = new File(base, "Mirati");
                    if (!folder.exists() && !folder.mkdirs()) return false;
                    try (FileOutputStream output = new FileOutputStream(new File(folder, safeName))) {
                        output.write(bytes);
                        output.flush();
                    }
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_FILE && fileChooserCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            fileChooserCallback.onReceiveValue(result);
            fileChooserCallback = null;
        }
    }

    private void showExitConfirmation() {
        if (exitDialogShowing || isFinishing()) return;
        exitDialogShowing = true;
        new AlertDialog.Builder(this)
            .setTitle("الخروج من مرآتي؟")
            .setMessage("هل تريد إنهاء التطبيق؟ ستبقى بياناتك المحفوظة على هذا الجهاز.")
            .setNegativeButton("البقاء", (dialog, which) -> {
                exitDialogShowing = false;
                dialog.dismiss();
            })
            .setPositiveButton("خروج", (dialog, which) -> {
                exitDialogShowing = false;
                finish();
            })
            .setOnCancelListener(dialog -> exitDialogShowing = false)
            .show();
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            showExitConfirmation();
            return;
        }
        webView.evaluateJavascript(
            "(function(){return window.miratiBack ? window.miratiBack() : false;})()",
            value -> {
                if (!"true".equals(value)) showExitConfirmation();
            }
        );
    }

    @Override
    protected void onDestroy() {
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(null);
            fileChooserCallback = null;
        }
        if (webView != null) {
            webView.removeJavascriptInterface("MiratiBridge");
            webView.destroy();
        }
        super.onDestroy();
    }
}

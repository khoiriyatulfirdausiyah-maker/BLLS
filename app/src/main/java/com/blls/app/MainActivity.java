package com.blls.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int REQUEST_FILE_CHOOSER = 1001;
    private static final int REQUEST_SAVE_JSON = 1002;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private String pendingJsonText;
    private String pendingJsonFilename;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback = filePathCallback;

                Intent intent;

                try {
                    intent = fileChooserParams.createIntent();
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                }

                intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");

                intent.putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        new String[]{
                                "application/json",
                                "text/json",
                                "text/plain",
                                "application/octet-stream"
                        }
                );

                try {
                    startActivityForResult(intent, REQUEST_FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "File manager tidak bisa dibuka.",
                            Toast.LENGTH_SHORT
                    ).show();

                    return false;
                }
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void saveJson(final String jsonText, final String filename) {

            runOnUiThread(() -> {

                pendingJsonText = jsonText;

                pendingJsonFilename =
                        (filename == null || filename.trim().isEmpty())
                                ? "BLLS-backup.json"
                                : filename;

                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);

                intent.addCategory(Intent.CATEGORY_OPENABLE);

                intent.setType("application/json");

                intent.putExtra(
                        Intent.EXTRA_TITLE,
                        pendingJsonFilename
                );

                try {

                    startActivityForResult(
                            intent,
                            REQUEST_SAVE_JSON
                    );

                } catch (Exception e) {

                    pendingJsonText = null;
                    pendingJsonFilename = null;

                    Toast.makeText(
                            MainActivity.this,
                            "Tidak bisa membuka dialog simpan.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == REQUEST_FILE_CHOOSER) {

            Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {

                if (data.getClipData() != null) {

                    int count =
                            data.getClipData().getItemCount();

                    results = new Uri[count];

                    for (int i = 0; i < count; i++) {

                        results[i] =
                                data.getClipData()
                                        .getItemAt(i)
                                        .getUri();
                    }

                } else if (data.getData() != null) {

                    results =
                            new Uri[]{
                                    data.getData()
                            };
                }
            }

            if (filePathCallback != null) {

                filePathCallback.onReceiveValue(results);

                filePathCallback = null;
            }

            return;
        }

        if (requestCode == REQUEST_SAVE_JSON) {

            if (
                    resultCode == RESULT_OK
                            && data != null
                            && data.getData() != null
                            && pendingJsonText != null
            ) {

                Uri uri = data.getData();

                try (
                        OutputStream os =
                                getContentResolver()
                                        .openOutputStream(
                                                uri,
                                                "w"
                                        )
                ) {

                    if (os == null) {
                        throw new Exception(
                                "OutputStream null"
                        );
                    }

                    os.write(
                            pendingJsonText
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    )
                    );

                    os.flush();

                    Toast.makeText(
                            this,
                            "Backup JSON berhasil disimpan.",
                            Toast.LENGTH_SHORT
                    ).show();

                } catch (Exception e) {

                    Toast.makeText(
                            this,
                            "Backup gagal disimpan.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }

            pendingJsonText = null;
            pendingJsonFilename = null;
        }
    }

    @Override
    public void onBackPressed() {

        if (
                webView != null
                        && webView.canGoBack()
        ) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}

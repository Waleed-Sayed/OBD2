package com.waleedsayed.obd2;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.waleedsayed.obd2.obd.ObdBridge;
import com.waleedsayed.obd2.obd.ObdManager;

/**
 * Hosts the single-page WebView UI in {@code assets/obd/index.html}.
 *
 * <p>Java handles Bluetooth OBD2 communication; the HTML/JS layer provides the user interface
 * that interacts with {@code NativeBridge} and listens to {@code window.OBD.emit(...)}.</p>
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String HOME_URL = "file:///android_asset/obd/index.html";
    private static final int REQ_BLUETOOTH = 200;

    private WebView webView;
    private ProgressBar progress;
    private ObdManager obd;
    private ObdBridge bridge;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.progress);

        configureWebView();

        obd = new ObdManager(getApplicationContext());
        bridge = new ObdBridge(this, webView, obd);
        obd.setListener(bridge);
        webView.addJavascriptInterface(bridge, "NativeBridge");

        webView.loadUrl(HOME_URL);

        requestBluetoothPermission();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                webView.evaluateJavascript(
                        "(window.OBD && typeof window.OBD.handleBack === 'function')"
                                + " ? window.OBD.handleBack() : false",
                        value -> {
                            if (!"true".equals(value)) {
                                finish();
                            }
                        });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bridge != null) {
            bridge.pushStatus();
        }
    }

    @Override
    protected void onDestroy() {
        if (bridge != null) {
            bridge.release();
        }
        if (obd != null) {
            obd.shutdown();
        }
        if (webView != null) {
            webView.removeJavascriptInterface("NativeBridge");
            webView.destroy();
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------------------------
    // WebView setup
    // ------------------------------------------------------------------------------------

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setTextZoom(100);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme())) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception e) {
                    Log.w(TAG, "No activity for " + uri, e);
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (bridge != null) {
                    bridge.pushStatus();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("WebView", consoleMessage.message()
                        + " @" + consoleMessage.lineNumber());
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BLUETOOTH);
    }
}

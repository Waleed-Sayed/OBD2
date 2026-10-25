package com.waleedsayed.obd2.obd;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The surface the WebView reaches native code through.
 *
 * <p>Every method annotated with {@link JavascriptInterface} runs on a WebView worker thread;
 * anything touching the UI is bounced to the main thread. Native results travel back through
 * {@code window.OBD.emit(event)} as JSON.</p>
 */
public final class ObdBridge implements ObdManager.Listener {

    private final Activity activity;
    private final WebView webView;
    private final ObdManager obd;

    public ObdBridge(Activity activity, WebView webView, ObdManager obd) {
        this.activity = activity;
        this.webView = webView;
        this.obd = obd;
    }

    /** Detaches the listener; call from the activity's onDestroy. */
    public void release() {
        obd.setListener(null);
    }

    // ------------------------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------------------------

    @JavascriptInterface
    public String getStatus() {
        JSONObject status = new JSONObject();
        try {
            status.put("connected", obd.isConnected());
        } catch (JSONException ignored) {
            // Primitive key; cannot fail.
        }
        return status.toString();
    }

    /** Pushes the same payload as {@link #getStatus()} but as an event. */
    public void pushStatus() {
        try {
            JSONObject event = new JSONObject(getStatus());
            event.put("type", "status");
            emit(event);
        } catch (JSONException ignored) {
            // As above.
        }
    }

    // ------------------------------------------------------------------------------------
    // OBD
    // ------------------------------------------------------------------------------------

    @JavascriptInterface
    public String getDevices() {
        return obd.bondedDevices().toString();
    }

    @JavascriptInterface
    public void connect(String address) {
        obd.connect(address);
    }

    @JavascriptInterface
    public void connectDemo() {
        obd.connect(ObdManager.DEMO_DEVICE);
    }

    @JavascriptInterface
    public void disconnect() {
        obd.disconnect();
    }

    @JavascriptInterface
    public void queryPid(String pid) {
        obd.queryPid(pid);
    }

    @JavascriptInterface
    public void sendRaw(String command) {
        obd.sendRaw(command);
    }

    @JavascriptInterface
    public void readDtcs() {
        obd.readDtcs();
    }

    @JavascriptInterface
    public void clearDtcs() {
        obd.clearDtcs();
    }

    @JavascriptInterface
    public void toast(final String message) {
        activity.runOnUiThread(() ->
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
    }

    // ------------------------------------------------------------------------------------
    // Native -> JS
    // ------------------------------------------------------------------------------------

    @Override
    public void onObdEvent(JSONObject event) {
        emit(event);
    }

    private void emit(final JSONObject event) {
        final String payload = event.toString();
        activity.runOnUiThread(() -> webView.evaluateJavascript(
                "window.OBD && window.OBD.emit && window.OBD.emit(" + payload + ");", null));
    }
}

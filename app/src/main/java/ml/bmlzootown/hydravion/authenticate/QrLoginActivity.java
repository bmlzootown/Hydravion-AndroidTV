package ml.bmlzootown.hydravion.authenticate;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import ml.bmlzootown.hydravion.R;
import ml.bmlzootown.hydravion.ThemeManager;
import ml.bmlzootown.hydravion.browse.MainFragment;

public class QrLoginActivity extends Activity {
    private static final String TAG = "QrLoginActivity";
    private static final String CLIENT_ID = "hydravion";
    private static final String SCOPE = "openid videos:watch account:read";
    private static final String DEVICE_AUTH_ENDPOINT = "https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/auth/device";
    private static final String TOKEN_ENDPOINT = "https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/token";

    private ImageView qrCodeView;
    private TextView statusTextView;
    private TextView userCodeDisplay;
    private TextView instructionsText;
    private TextView cookieSteps;
    private TextView timerText;
    private RequestQueue requestQueue;

    private String deviceCode;
    private String userCode;
    private String verificationUri;
    private String verificationUriComplete;
    private long expiresAtMs;
    private long pollIntervalMs = 5000L;
    private android.os.Handler handler;
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollForToken();
        }
    };
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimer();
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_login);
        
        qrCodeView = findViewById(R.id.qr_code);
        statusTextView = findViewById(R.id.status_text);
        userCodeDisplay = findViewById(R.id.user_code_display);
        instructionsText = findViewById(R.id.instructions_text);
        cookieSteps = findViewById(R.id.cookie_steps);
        timerText = findViewById(R.id.timer_text);
        requestQueue = Volley.newRequestQueue(this);
        handler = new android.os.Handler();

        startDeviceAuthorization();
    }

    private void startDeviceAuthorization() {
        statusTextView.setText("Requesting login code...");

        StringRequest request = new StringRequest(
                Request.Method.POST,
                DEVICE_AUTH_ENDPOINT,
                this::handleDeviceAuthResponse,
                error -> {
                    Log.e(TAG, "Device authorization error", error);
                    Toast.makeText(this, "Failed to start device login.", Toast.LENGTH_LONG).show();
                    finish();
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("client_id", CLIENT_ID);
                params.put("scope", SCOPE);
                return params;
            }
        };

        requestQueue.add(request);
    }
    
    private void handleDeviceAuthResponse(String response) {
        try {
            JSONObject json = new JSONObject(response);
            deviceCode = json.getString("device_code");
            userCode = json.getString("user_code");
            verificationUri = json.getString("verification_uri");
            verificationUriComplete = json.optString("verification_uri_complete", verificationUri);
            long expiresIn = json.optLong("expires_in", 600L);
            long interval = json.optLong("interval", 5L);

            expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L;
            pollIntervalMs = interval * 1000L;

            // Generate QR code with verification_uri_complete (on-device, no external service)
            generateQRCode(verificationUriComplete);

            // Display user code in highlighted area
            userCodeDisplay.setText(userCode);
            
            // Update instructions text with bold floatplane.com/link
            updateInstructionsWithBoldUrl();
            
            statusTextView.setText("Waiting for you to complete login on another device...");

            // Start timer countdown
            handler.post(timerRunnable);

            // Start polling token endpoint
            handler.postDelayed(pollRunnable, pollIntervalMs);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse device auth response", e);
            Toast.makeText(this, "Invalid response from login server.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void generateQRCode(String url) {
        try {
            // Generate QR code on-device using local library (no external service)
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            // Generate bitmap - use size that matches view (280dp = ~840px at 3x density)
            Bitmap originalBitmap = barcodeEncoder.encodeBitmap(url, BarcodeFormat.QR_CODE, 840, 840);
            
            // Crop white borders to remove quiet zone padding
            Bitmap croppedBitmap = cropWhiteBorders(originalBitmap);
            
            // Recycle the original large bitmap if a new cropped bitmap was created
            if (croppedBitmap != originalBitmap && !originalBitmap.isRecycled()) {
                originalBitmap.recycle();
            }
            
            qrCodeView.setImageBitmap(croppedBitmap);
        } catch (WriterException e) {
            Log.e(TAG, "Error generating QR code", e);
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show();
        }
    }
    
    private Bitmap cropWhiteBorders(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // Find top border
        int top = 0;
        for (int y = 0; y < height; y++) {
            boolean hasNonWhite = false;
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                if ((pixel & 0x00FFFFFF) != 0x00FFFFFF) { // Not white
                    hasNonWhite = true;
                    break;
                }
            }
            if (hasNonWhite) {
                top = y;
                break;
            }
        }
        
        // Find bottom border
        int bottom = height - 1;
        for (int y = height - 1; y >= 0; y--) {
            boolean hasNonWhite = false;
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                if ((pixel & 0x00FFFFFF) != 0x00FFFFFF) { // Not white
                    hasNonWhite = true;
                    break;
                }
            }
            if (hasNonWhite) {
                bottom = y;
                break;
            }
        }
        
        // Find left border
        int left = 0;
        for (int x = 0; x < width; x++) {
            boolean hasNonWhite = false;
            for (int y = top; y <= bottom; y++) {
                int pixel = bitmap.getPixel(x, y);
                if ((pixel & 0x00FFFFFF) != 0x00FFFFFF) { // Not white
                    hasNonWhite = true;
                    break;
                }
            }
            if (hasNonWhite) {
                left = x;
                break;
            }
        }
        
        // Find right border
        int right = width - 1;
        for (int x = width - 1; x >= 0; x--) {
            boolean hasNonWhite = false;
            for (int y = top; y <= bottom; y++) {
                int pixel = bitmap.getPixel(x, y);
                if ((pixel & 0x00FFFFFF) != 0x00FFFFFF) { // Not white
                    hasNonWhite = true;
                    break;
                }
            }
            if (hasNonWhite) {
                right = x;
                break;
            }
        }
        
        // Crop to actual QR code bounds
        if (left < right && top < bottom) {
            return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1);
        }
        
        return bitmap;
    }
    
    private void updateTimer() {
        if (expiresAtMs == 0) {
            return;
        }
        
        long remainingMs = expiresAtMs - System.currentTimeMillis();
        
        if (remainingMs <= 0) {
            // Timer expired, refresh the QR code and user code
            timerText.setText("Time Remaining: 0:00");
            statusTextView.setText("Login code expired. Refreshing...");
            handler.removeCallbacks(pollRunnable);
            handler.removeCallbacks(timerRunnable);
            startDeviceAuthorization();
            return;
        }
        
        long remainingSeconds = remainingMs / 1000L;
        long minutes = remainingSeconds / 60L;
        long seconds = remainingSeconds % 60L;
        
        String timerString = String.format("Time Remaining: %d:%02d", minutes, seconds);
        timerText.setText(timerString);
        
        // Update timer every second
        handler.postDelayed(timerRunnable, 1000L);
    }
    
    private void updateInstructionsWithBoldUrl() {
        // Bold "floatplane.com/link" in instructions text
        String instructions = instructionsText.getText().toString();
        SpannableString spannable = new SpannableString(instructions);
        int start = instructions.indexOf("floatplane.com/link");
        if (start >= 0) {
            int end = start + "floatplane.com/link".length();
            spannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        instructionsText.setText(spannable);
        
        // Bold "floatplane.com/link" in steps text
        String steps = cookieSteps.getText().toString();
        SpannableString stepsSpannable = new SpannableString(steps);
        int stepsStart = steps.indexOf("floatplane.com/link");
        if (stepsStart >= 0) {
            int stepsEnd = stepsStart + "floatplane.com/link".length();
            stepsSpannable.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), stepsStart, stepsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        cookieSteps.setText(stepsSpannable);
    }
    
    private void pollForToken() {
        if (deviceCode == null) {
            return;
        }

        if (System.currentTimeMillis() >= expiresAtMs) {
            // Timer will handle the refresh automatically
            return;
        }

        StringRequest request = new StringRequest(
                Request.Method.POST,
                TOKEN_ENDPOINT,
                this::handleTokenResponse,
                error -> {
                    Log.e(TAG, "Token polling error", error);
                    // Keep polling on transient errors
                    handler.postDelayed(pollRunnable, pollIntervalMs);
                }
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
                params.put("client_id", CLIENT_ID);
                params.put("device_code", deviceCode);
                return params;
            }
        };

        requestQueue.add(request);
    }

    private void handleTokenResponse(String response) {
        try {
            JSONObject json = new JSONObject(response);
            if (json.has("error")) {
                String error = json.getString("error");
                if ("authorization_pending".equals(error)) {
                    // keep polling
                    handler.postDelayed(pollRunnable, pollIntervalMs);
                    return;
                } else if ("slow_down".equals(error)) {
                    pollIntervalMs += 2000L;
                    handler.postDelayed(pollRunnable, pollIntervalMs);
                    return;
                } else if ("expired_token".equals(error)) {
                    statusTextView.setText("Login code expired. Refreshing...");
                    handler.removeCallbacks(pollRunnable);
                    handler.removeCallbacks(timerRunnable);
                    startDeviceAuthorization();
                    return;
                } else {
                    statusTextView.setText("Login failed: " + error);
                    return;
                }
            }

            String accessToken = json.getString("access_token");
            String refreshToken = json.optString("refresh_token", "");
            long expiresIn = json.optLong("expires_in", 3600L);

            handleLoginSuccess(accessToken, refreshToken, expiresIn);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse token response", e);
            statusTextView.setText("Failed to complete login.");
        }
    }

    private void handleLoginSuccess(String accessToken, String refreshToken, long expiresInSeconds) {
        // Stop all handlers before finishing
        if (handler != null) {
            handler.removeCallbacks(pollRunnable);
            handler.removeCallbacks(timerRunnable);
        }
        
        Intent intent = new Intent();
        intent.putExtra("access_token", accessToken);
        intent.putExtra("refresh_token", refreshToken);
        intent.putExtra("expires_in", expiresInSeconds);
        setResult(RESULT_OK, intent);
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacks(pollRunnable);
            handler.removeCallbacks(timerRunnable);
        }
    }
}


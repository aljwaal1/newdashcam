package com.explapp.newdashcamlegacy;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FixedMainActivity extends Activity implements SurfaceHolder.Callback, LocationListener {
    private static final int REQ = 9090;
    private final Handler handler = new Handler();
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.US);

    private Camera camera;
    private MediaRecorder recorder;
    private SurfaceView preview;
    private Button recordButton;
    private TextView timeText;
    private TextView speedText;
    private TextView statusText;
    private boolean recording;
    private File outputFile;
    private LocationManager locationManager;

    private final Runnable clockTask = new Runnable() {
        @Override public void run() {
            if (timeText != null) timeText.setText(clockFormat.format(new Date()));
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        requestNeededPermissions();
        handler.post(clockTask);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        preview.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(14, 6, 14, 6);
        top.setBackgroundColor(Color.argb(170, 0, 0, 0));

        LinearLayout stamp = new LinearLayout(this);
        stamp.setOrientation(LinearLayout.VERTICAL);
        timeText = makeText("---- -- --  --:--:--", 18, Color.WHITE);
        speedText = makeText("GPS...", 18, Color.rgb(255, 193, 7));
        stamp.addView(timeText);
        stamp.addView(speedText);
        top.addView(stamp, new LinearLayout.LayoutParams(0, -2, 1f));

        statusText = makeText("جاهز", 17, Color.rgb(74, 222, 128));
        statusText.setGravity(Gravity.CENTER);
        top.addView(statusText, new LinearLayout.LayoutParams(130, 52));

        root.addView(top, new FrameLayout.LayoutParams(-1, 72, Gravity.TOP));

        recordButton = new Button(this);
        recordButton.setText("بدء التسجيل");
        recordButton.setTextColor(Color.WHITE);
        recordButton.setTextSize(18);
        recordButton.setBackgroundColor(Color.rgb(220, 38, 38));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(280, 76, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        p.bottomMargin = 18;
        root.addView(recordButton, p);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (recording) stopRecording(); else startRecording();
            }
        });
        setContentView(root);
    }

    private TextView makeText(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT < 23) {
            startLocation();
            return;
        }
        boolean cameraOk = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean storageOk = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean locationOk = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!cameraOk || !storageOk || !locationOk) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION}, REQ);
        } else {
            startLocation();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ) startLocation();
    }

    private void startLocation() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                speedText.setText("GPS غير مسموح");
                return;
            }
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                speedText.setText("GPS مغلق");
                return;
            }
            speedText.setText("GPS...");
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this);
        } catch (Exception e) {
            speedText.setText("GPS غير متاح");
        }
    }

    @Override public void onLocationChanged(Location location) {
        double kmh = Math.max(0d, location.getSpeed() * 3.6d);
        speedText.setText("GPS " + Math.round(kmh) + " km/h");
    }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onProviderEnabled(String provider) { speedText.setText("GPS..."); }
    @Override public void onProviderDisabled(String provider) { speedText.setText("GPS مغلق"); }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            Camera.Parameters params = camera.getParameters();
            List<String> modes = params.getSupportedFocusModes();
            if (modes != null && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                camera.setParameters(params);
            }
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح الكاميرا", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) { releaseAll(); }

    private void startRecording() {
        if (camera == null || recording) return;
        File dir = new File(Environment.getExternalStorageDirectory(), "ExplAppDashCam");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "تعذر إنشاء مجلد الحفظ", Toast.LENGTH_LONG).show();
            return;
        }

        if (tryStart(dir, MediaRecorder.OutputFormat.THREE_GPP, MediaRecorder.VideoEncoder.H263, 352, 288, 15, 384000, ".3gp")) return;
        resetCameraAfterFailedStart();
        if (tryStart(dir, MediaRecorder.OutputFormat.MPEG_4, MediaRecorder.VideoEncoder.H264, 640, 480, 15, 700000, ".mp4")) return;
        resetCameraAfterFailedStart();
        if (tryStart(dir, MediaRecorder.OutputFormat.THREE_GPP, MediaRecorder.VideoEncoder.H263, 176, 144, 15, 256000, ".3gp")) return;

        resetCameraAfterFailedStart();
        statusText.setText("فشل التسجيل");
        statusText.setTextColor(Color.rgb(248, 113, 113));
        Toast.makeText(this, "تعذر بدء التسجيل", Toast.LENGTH_LONG).show();
    }

    private boolean tryStart(File dir, int format, int encoder, int width, int height, int fps, int bitrate, String extension) {
        outputFile = new File(dir, "dashcam_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + extension);
        try {
            camera.stopPreview();
            camera.unlock();
            recorder = new MediaRecorder();
            recorder.setCamera(camera);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            recorder.setOutputFormat(format);
            recorder.setVideoEncoder(encoder);
            recorder.setVideoSize(width, height);
            recorder.setVideoFrameRate(fps);
            recorder.setVideoEncodingBitRate(bitrate);
            recorder.setPreviewDisplay(preview.getHolder().getSurface());
            recorder.setOutputFile(outputFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            recordButton.setText("إيقاف وحفظ");
            statusText.setText("● تسجيل");
            statusText.setTextColor(Color.rgb(248, 113, 113));
            return true;
        } catch (Exception e) {
            if (outputFile != null && outputFile.exists()) outputFile.delete();
            releaseRecorder();
            return false;
        }
    }

    private void resetCameraAfterFailedStart() {
        releaseRecorder();
        try {
            camera.lock();
            camera.reconnect();
            camera.setPreviewDisplay(preview.getHolder());
            camera.startPreview();
        } catch (Exception ignored) {}
    }

    private void stopRecording() {
        boolean saved = false;
        try {
            recorder.stop();
            saved = outputFile != null && outputFile.exists() && outputFile.length() > 0;
        } catch (Exception e) {
            if (outputFile != null && outputFile.exists()) outputFile.delete();
        }
        releaseRecorder();
        try {
            camera.lock();
            camera.reconnect();
            camera.setPreviewDisplay(preview.getHolder());
            camera.startPreview();
        } catch (Exception ignored) {}
        recording = false;
        recordButton.setText("بدء التسجيل");
        statusText.setText("جاهز");
        statusText.setTextColor(Color.rgb(74, 222, 128));

        if (saved) {
            String mime = outputFile.getName().endsWith(".3gp") ? "video/3gpp" : "video/mp4";
            MediaScannerConnection.scanFile(this, new String[]{outputFile.getAbsolutePath()}, new String[]{mime}, null);
            Toast.makeText(this, "تم حفظ الفيديو", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "لم يتم حفظ الفيديو", Toast.LENGTH_SHORT).show();
        }
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.reset(); } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
    }

    private void releaseAll() {
        if (recording) stopRecording();
        releaseRecorder();
        if (camera != null) {
            try { camera.stopPreview(); } catch (Exception ignored) {}
            try { camera.release(); } catch (Exception ignored) {}
            camera = null;
        }
    }

    @Override protected void onPause() {
        releaseAll();
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) {}
        }
        releaseAll();
        super.onDestroy();
    }
}

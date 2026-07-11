package com.explapp.newdashcamlegacy;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.Surface;
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
import java.util.Locale;

public class MainActivity extends Activity implements SurfaceHolder.Callback, LocationListener {
    private static final int REQ_PERMISSIONS = 4004;
    private static final int RED = Color.rgb(229, 57, 53);
    private static final int GREEN = Color.rgb(34, 197, 94);
    private static final int AMBER = Color.rgb(245, 166, 35);
    private static final int PANEL = Color.rgb(20, 25, 31);
    private static final int PANEL_BORDER = Color.rgb(55, 65, 75);
    private static final int TEXT = Color.rgb(232, 236, 239);
    private static final int MUTED = Color.rgb(148, 163, 184);

    private final Handler handler = new Handler();
    private final SimpleDateFormat stampFormat = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.US);
    private final SimpleDateFormat fileFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

    private Camera camera;
    private MediaRecorder recorder;
    private SurfaceView preview;
    private SurfaceHolder surfaceHolder;
    private TextView timeText;
    private TextView speedText;
    private TextView statusText;
    private TextView savedText;
    private Button recordButton;
    private Button switchButton;
    private Button segmentButton;

    private boolean recording;
    private boolean surfaceReady;
    private int cameraId;
    private int segmentMinutes = 3;
    private String currentOutputPath;
    private LocationManager locationManager;

    private final Runnable clockTask = new Runnable() {
        @Override public void run() {
            if (timeText != null) timeText.setText(stampFormat.format(new Date()));
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable segmentTask = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            stopRecording(false);
            handler.postDelayed(new Runnable() {
                @Override public void run() { if (surfaceReady) startRecording(); }
            }, 350);
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
        cameraId = findBackCamera();
        buildUi();
        requestNeededPermissions();
        handler.post(clockTask);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        preview = new SurfaceView(this);
        surfaceHolder = preview.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        root.addView(preview, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout topBar = horizontal();
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        topBar.setBackgroundColor(Color.argb(185, 8, 12, 16));

        LinearLayout stamp = vertical();
        timeText = text("---- -- --  --:--:--", 20, Color.WHITE, true);
        speedText = text("GPS -- km/h", 18, AMBER, true);
        stamp.addView(timeText);
        stamp.addView(speedText);
        topBar.addView(stamp, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        statusText = text("جاهز", 17, GREEN, true);
        statusText.setGravity(Gravity.CENTER);
        statusText.setBackground(rounded(Color.argb(80, 34, 197, 94), 18, GREEN, 2));
        topBar.addView(statusText, new LinearLayout.LayoutParams(dp(110), dp(46)));

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(72), Gravity.TOP);
        root.addView(topBar, topParams);

        LinearLayout sidePanel = vertical();
        sidePanel.setGravity(Gravity.CENTER);
        sidePanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        sidePanel.setBackgroundColor(Color.argb(185, 8, 12, 16));

        switchButton = actionButton("تبديل\nالكاميرا", Color.rgb(2, 132, 199));
        switchButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchCamera(); }
        });
        sidePanel.addView(switchButton, panelButtonParams());

        segmentButton = actionButton("المقطع\n3 دقائق", Color.rgb(217, 119, 6));
        segmentButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleSegment(); }
        });
        sidePanel.addView(segmentButton, panelButtonParams());

        FrameLayout.LayoutParams sideParams = new FrameLayout.LayoutParams(dp(130), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        sideParams.rightMargin = dp(12);
        root.addView(sidePanel, sideParams);

        LinearLayout bottomBar = horizontal();
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        bottomBar.setBackgroundColor(Color.argb(205, 8, 12, 16));

        LinearLayout savedBox = vertical();
        savedBox.addView(text("آخر ملف محفوظ", 13, MUTED, false));
        savedText = text("لا يوجد تسجيل بعد", 15, TEXT, true);
        savedBox.addView(savedText);
        bottomBar.addView(savedBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        recordButton = actionButton("بدء التسجيل", RED);
        recordButton.setTextSize(19);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (recording) stopRecording(true); else startRecording(); }
        });
        bottomBar.addView(recordButton, new LinearLayout.LayoutParams(dp(190), dp(58)));

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(78), Gravity.BOTTOM);
        root.addView(bottomBar, bottomParams);
        setContentView(root);
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
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION}, REQ_PERMISSIONS);
        } else {
            startLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_PERMISSIONS) {
            startLocation();
            if (surfaceReady && camera == null) openCamera();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        openCamera();
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releaseAll();
    }

    private void openCamera() {
        if (!surfaceReady || camera != null) return;
        try {
            camera = Camera.open(cameraId);
            setCameraDisplayOrientation(cameraId, camera);
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFocusMode(bestFocusMode(parameters));
            camera.setParameters(parameters);
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
            setStatus("جاهز", GREEN);
        } catch (Exception e) {
            setStatus("خطأ كاميرا", RED);
            Toast.makeText(this, "تعذر فتح الكاميرا", Toast.LENGTH_LONG).show();
            releaseCamera();
        }
    }

    private String bestFocusMode(Camera.Parameters p) {
        if (p.getSupportedFocusModes() != null && p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) return Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
        if (p.getSupportedFocusModes() != null && p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) return Camera.Parameters.FOCUS_MODE_AUTO;
        return p.getFocusMode();
    }

    private void switchCamera() {
        if (recording) {
            Toast.makeText(this, "أوقف التسجيل قبل تبديل الكاميرا", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Camera.getNumberOfCameras() < 2) {
            Toast.makeText(this, "لا توجد كاميرا ثانية", Toast.LENGTH_SHORT).show();
            return;
        }
        releaseCamera();
        cameraId = (cameraId + 1) % Camera.getNumberOfCameras();
        openCamera();
    }

    private void cycleSegment() {
        if (segmentMinutes == 1) segmentMinutes = 3;
        else if (segmentMinutes == 3) segmentMinutes = 5;
        else segmentMinutes = 1;
        segmentButton.setText("المقطع\n" + segmentMinutes + " دقائق");
        if (recording) scheduleSegment();
    }

    private void startRecording() {
        if (recording || camera == null || !surfaceReady) return;
        File dir = recordingsDir();
        if (dir == null) return;
        currentOutputPath = new File(dir, "dashcam_" + fileFormat.format(new Date()) + ".mp4").getAbsolutePath();
        try {
            camera.stopPreview();
            camera.unlock();
            recorder = new MediaRecorder();
            recorder.setCamera(camera);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            CamcorderProfile profile = chooseProfile(cameraId);
            recorder.setProfile(profile);
            recorder.setOutputFile(currentOutputPath);
            recorder.setPreviewDisplay(surfaceHolder.getSurface());
            recorder.setOrientationHint(recordingOrientation(cameraId));
            recorder.prepare();
            recorder.start();
            recording = true;
            recordButton.setText("إيقاف وحفظ");
            recordButton.setBackground(rounded(Color.rgb(153, 27, 27), 18, RED, 2));
            switchButton.setEnabled(false);
            switchButton.setAlpha(.45f);
            setStatus("● تسجيل", RED);
            scheduleSegment();
        } catch (Exception e) {
            releaseRecorder();
            try { camera.lock(); camera.startPreview(); } catch (Exception ignored) { }
            setStatus("فشل التسجيل", RED);
            Toast.makeText(this, "تعذر بدء التسجيل", Toast.LENGTH_LONG).show();
        }
    }

    private CamcorderProfile chooseProfile(int id) {
        if (CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_720P)) return CamcorderProfile.get(id, CamcorderProfile.QUALITY_720P);
        if (CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_480P)) return CamcorderProfile.get(id, CamcorderProfile.QUALITY_480P);
        return CamcorderProfile.get(id, CamcorderProfile.QUALITY_LOW);
    }

    private void scheduleSegment() {
        handler.removeCallbacks(segmentTask);
        handler.postDelayed(segmentTask, segmentMinutes * 60L * 1000L);
    }

    private void stopRecording(boolean notify) {
        if (!recording) return;
        handler.removeCallbacks(segmentTask);
        boolean saved = false;
        try {
            recorder.stop();
            saved = currentOutputPath != null && new File(currentOutputPath).exists() && new File(currentOutputPath).length() > 0;
        } catch (Exception e) {
            if (currentOutputPath != null) new File(currentOutputPath).delete();
        }
        releaseRecorder();
        try {
            camera.lock();
            camera.reconnect();
            setCameraDisplayOrientation(cameraId, camera);
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (Exception ignored) { }
        recording = false;
        recordButton.setText("بدء التسجيل");
        recordButton.setBackground(rounded(RED, 18, Color.rgb(255, 120, 120), 2));
        switchButton.setEnabled(true);
        switchButton.setAlpha(1f);
        setStatus("جاهز", GREEN);
        if (saved) {
            savedText.setText(new File(currentOutputPath).getName());
            cleanupOldFiles(recordingsDir(), 30);
            if (notify) Toast.makeText(this, "تم حفظ الفيديو", Toast.LENGTH_SHORT).show();
        } else if (notify) {
            Toast.makeText(this, "لم يتم حفظ الفيديو", Toast.LENGTH_SHORT).show();
        }
    }

    private File recordingsDir() {
        try {
            File base = Environment.getExternalStorageDirectory();
            File dir = new File(base, "ExplAppDashCam");
            if (!dir.exists() && !dir.mkdirs()) {
                Toast.makeText(this, "تعذر إنشاء مجلد التسجيلات", Toast.LENGTH_LONG).show();
                return null;
            }
            return dir;
        } catch (Exception e) {
            return null;
        }
    }

    private void cleanupOldFiles(File dir, int keep) {
        if (dir == null) return;
        File[] files = dir.listFiles();
        if (files == null || files.length <= keep) return;
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.valueOf(b.lastModified()).compareTo(a.lastModified()); }
        });
        for (int i = keep; i < files.length; i++) if (files[i].isFile()) files[i].delete();
    }

    private void startLocation() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        } catch (Exception ignored) { }
    }

    @Override public void onLocationChanged(Location location) {
        double speed = Math.max(0, location.getSpeed() * 3.6);
        speedText.setText("GPS " + Math.round(speed) + " km/h");
    }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { speedText.setText("GPS -- km/h"); }

    private int findBackCamera() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i;
        }
        return 0;
    }

    private void setCameraDisplayOrientation(int id, Camera cam) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(id, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        cam.setDisplayOrientation(result);
    }

    private int recordingOrientation(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(id, info);
        return info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? (360 - info.orientation) % 360 : info.orientation;
    }

    private void setStatus(String value, int color) {
        statusText.setText(value);
        statusText.setTextColor(color);
        statusText.setBackground(rounded(Color.argb(75, Color.red(color), Color.green(color), Color.blue(color)), 18, color, 2));
    }

    private Button actionButton(String value, int color) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(5), dp(3), dp(5), dp(3));
        b.setBackground(rounded(color, 18, lighten(color), 2));
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }

    private LinearLayout.LayoutParams panelButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
        p.setMargins(0, dp(5), 0, dp(5));
        return p;
    }

    private GradientDrawable rounded(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(strokeWidth), stroke);
        return d;
    }

    private int lighten(int color) {
        return Color.rgb(Math.min(255, Color.red(color) + 55), Math.min(255, Color.green(color) + 55), Math.min(255, Color.blue(color) + 55));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.reset(); } catch (Exception ignored) { }
            try { recorder.release(); } catch (Exception ignored) { }
            recorder = null;
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            try { camera.stopPreview(); } catch (Exception ignored) { }
            try { camera.release(); } catch (Exception ignored) { }
            camera = null;
        }
    }

    private void releaseAll() {
        if (recording) stopRecording(false);
        releaseRecorder();
        releaseCamera();
    }

    @Override protected void onPause() {
        super.onPause();
        releaseAll();
    }

    @Override protected void onResume() {
        super.onResume();
        if (surfaceReady && camera == null) openCamera();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        }
        releaseAll();
        super.onDestroy();
    }
}

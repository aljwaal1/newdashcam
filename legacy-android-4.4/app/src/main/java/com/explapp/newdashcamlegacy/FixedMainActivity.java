package com.explapp.newdashcamlegacy;

import android.app.Activity;
import android.graphics.Color;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FixedMainActivity extends Activity implements SurfaceHolder.Callback {
    private Camera camera;
    private MediaRecorder recorder;
    private SurfaceView preview;
    private Button recordButton;
    private boolean recording;
    private File outputFile;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        preview.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        recordButton = new Button(this);
        recordButton.setText("بدء التسجيل");
        recordButton.setTextColor(Color.WHITE);
        recordButton.setBackgroundColor(Color.rgb(220, 38, 38));
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(260, 72, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        p.bottomMargin = 18;
        root.addView(recordButton, p);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (recording) stopRecording(); else startRecording();
            }
        });
        setContentView(root);
    }

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

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        releaseAll();
    }

    private void startRecording() {
        if (camera == null || recording) return;
        File dir = new File(Environment.getExternalStorageDirectory(), "ExplAppDashCam");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "تعذر إنشاء مجلد الحفظ", Toast.LENGTH_LONG).show();
            return;
        }
        outputFile = new File(dir, "dashcam_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4");

        try {
            camera.unlock();
            recorder = new MediaRecorder();
            recorder.setCamera(camera);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

            Camera.Size size = chooseSafeSize(camera.getParameters().getSupportedVideoSizes(), camera.getParameters().getSupportedPreviewSizes());
            recorder.setVideoSize(size.width, size.height);
            recorder.setVideoFrameRate(15);
            recorder.setVideoEncodingBitRate(1200000);
            recorder.setOrientationHint(90);
            recorder.setPreviewDisplay(preview.getHolder().getSurface());
            recorder.setOutputFile(outputFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();

            recording = true;
            recordButton.setText("إيقاف وحفظ");
        } catch (Exception e) {
            releaseRecorder();
            try { camera.lock(); camera.startPreview(); } catch (Exception ignored) {}
            if (outputFile != null && outputFile.exists()) outputFile.delete();
            Toast.makeText(this, "تعذر بدء التسجيل", Toast.LENGTH_LONG).show();
        }
    }

    private Camera.Size chooseSafeSize(List<Camera.Size> videoSizes, List<Camera.Size> previewSizes) {
        List<Camera.Size> sizes = (videoSizes != null && !videoSizes.isEmpty()) ? videoSizes : previewSizes;
        Camera.Size best = sizes.get(0);
        for (Camera.Size size : sizes) {
            if (size.width == 640 && size.height == 480) return size;
            if (size.width <= 640 && size.height <= 480 && size.width * size.height > best.width * best.height) best = size;
        }
        return best;
    }

    private void stopRecording() {
        try {
            recorder.stop();
            if (outputFile != null) {
                MediaScannerConnection.scanFile(this, new String[]{outputFile.getAbsolutePath()}, new String[]{"video/mp4"}, null);
            }
            Toast.makeText(this, "تم حفظ الفيديو", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            if (outputFile != null && outputFile.exists()) outputFile.delete();
            Toast.makeText(this, "لم يتم حفظ الفيديو", Toast.LENGTH_SHORT).show();
        }
        releaseRecorder();
        try { camera.lock(); camera.startPreview(); } catch (Exception ignored) {}
        recording = false;
        recordButton.setText("بدء التسجيل");
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
}

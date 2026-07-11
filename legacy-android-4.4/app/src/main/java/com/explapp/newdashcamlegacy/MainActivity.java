package com.explapp.newdashcamlegacy;

import android.app.Activity;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private Camera camera;
    private MediaRecorder recorder;
    private SurfaceView preview;
    private Button recordButton;
    private boolean recording;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        recordButton = new Button(this);
        recordButton.setText("بدء التسجيل");
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        p.bottomMargin = 24;
        root.addView(recordButton, p);
        recordButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (recording) stopRecording(); else startRecording(); } });
        setContentView(root);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        try { camera = Camera.open(); camera.setDisplayOrientation(90); camera.setPreviewDisplay(holder); camera.startPreview(); }
        catch (Exception e) { Toast.makeText(this, "تعذر فتح الكاميرا", Toast.LENGTH_LONG).show(); }
    }
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    public void surfaceDestroyed(SurfaceHolder holder) { releaseAll(); }

    private void startRecording() {
        try {
            if (camera == null) return;
            File dir = new File(Environment.getExternalStorageDirectory(), "ExplAppDashCam");
            if (!dir.exists()) dir.mkdirs();
            String name = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
            camera.unlock();
            recorder = new MediaRecorder();
            recorder.setCamera(camera);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoSize(640, 480);
            recorder.setVideoFrameRate(24);
            recorder.setVideoEncodingBitRate(2000000);
            recorder.setOutputFile(new File(dir, name).getAbsolutePath());
            recorder.setPreviewDisplay(preview.getHolder().getSurface());
            recorder.prepare(); recorder.start();
            recording = true; recordButton.setText("إيقاف وحفظ");
        } catch (Exception e) { releaseRecorder(); try { camera.lock(); } catch (Exception ignored) {} Toast.makeText(this, "تعذر بدء التسجيل", Toast.LENGTH_LONG).show(); }
    }

    private void stopRecording() {
        try { recorder.stop(); Toast.makeText(this, "تم حفظ الفيديو", Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
        releaseRecorder();
        try { camera.lock(); camera.startPreview(); } catch (Exception ignored) {}
        recording = false; recordButton.setText("بدء التسجيل");
    }

    private void releaseRecorder() { if (recorder != null) { try { recorder.reset(); recorder.release(); } catch (Exception ignored) {} recorder = null; } }
    private void releaseAll() { if (recording) stopRecording(); releaseRecorder(); if (camera != null) { try { camera.stopPreview(); camera.release(); } catch (Exception ignored) {} camera = null; } }
    @Override protected void onPause() { super.onPause(); releaseAll(); }
}

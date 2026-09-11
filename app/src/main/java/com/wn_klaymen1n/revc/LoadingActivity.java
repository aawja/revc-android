package com.wn_klaymen1n.revc;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.libsdl.app.SDLActivity;

import java.io.File;

public class LoadingActivity extends Activity {
    public static final String EXTRA_GAME_PATH = "game_path";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearProgressIndicator progressIndicator;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.i("Loading", "onCreate started");
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_loading);

        progressIndicator = findViewById(R.id.loadingProgress);
        statusText = findViewById(R.id.loadingStatus);

        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        String gamePath = getIntent().getStringExtra(EXTRA_GAME_PATH);
        Logger.d("Loading", "Received game path: " + gamePath);
        if (gamePath == null || gamePath.isEmpty()) {
            Logger.e("Loading", "Game path is null or empty, finishing activity");
            finish();
            return;
        }

        LauncherActivity.setCurrentGamePath(gamePath);
        Logger.i("Loading", "Starting game preparation thread");
        new Thread(() -> prepareAndLaunch(gamePath), "revc-loader").start();
    }

    private void prepareAndLaunch(String gamePath) {
        Logger.d("Loading", "prepareAndLaunch started for path: " + gamePath);
        postProgress(10, getString(R.string.loading_checking_files));

        File gameImg = new File(gamePath, "models/gta3.img");
        Logger.d("Loading", "Checking for gta3.img at: " + gameImg.getAbsolutePath());
        if (!gameImg.exists()) {
            Logger.e("Loading", "gta3.img not found, aborting launch");
            handler.post(this::finish);
            return;
        }
        Logger.i("Loading", "gta3.img found, size: " + gameImg.length() + " bytes");

        sleepBriefly(120);
        postProgress(35, getString(R.string.loading_copying_assets));
        Logger.i("Loading", "Copying mobile UI assets");
        LauncherActivity.copyMobileUiAssets(this, gamePath);

        sleepBriefly(160);
        postProgress(70, getString(R.string.loading_preparing_runtime));
        LauncherActivity.setCurrentGamePath(gamePath);
        Logger.d("Loading", "Runtime preparation complete");

        sleepBriefly(140);
        postProgress(100, getString(R.string.loading_starting_game));
        Logger.i("Loading", "Starting SDL activity");
        sleepBriefly(180);

        handler.post(() -> {
            Intent intent = new Intent(LoadingActivity.this, SDLActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            Logger.i("Loading", "SDL activity started, finishing LoadingActivity");
            finish();
        });
    }

    private void postProgress(int progress, String status) {
        handler.post(() -> {
            progressIndicator.setProgressCompat(progress, true);
            statusText.setText(status);
        });
    }

    private void sleepBriefly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

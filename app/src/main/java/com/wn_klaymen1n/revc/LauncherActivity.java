package com.wn_klaymen1n.revc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.os.Build;
import android.view.MenuItem;
import android.view.Window;
import android.view.View;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.provider.Settings;
import android.net.Uri;

import org.libsdl.app.SDLActivity;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class LauncherActivity extends Activity {
    public static native void setenv(String value);
    private static String currentGamePath;
    private static final List<String> MOBILE_UI_ASSETS = Arrays.asList(
            "mobileui/ArcadeJoystick_Base.png",
            "mobileui/hud_circle.png",
            "mobileui/hud_analognub.png",
            "mobileui/hud_car.png",
            "mobileui/punch.png",
            "mobileui/sprint.png",
            "mobileui/hud_left.png",
            "mobileui/hud_right.png",
            "mobileui/brake.png",
            "mobileui/accelerate.png"
    );

    static public EditText editText;
    private static final int REQUEST_PERMISSION = 1001;
    private static final int REQUEST_MANAGE_STORAGE = 1002;
    private static LauncherActivity lastInstance;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.init();
        Logger.i("Launcher", "onCreate started");
        lastInstance = this;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);

        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        Logger.d("Launcher", "SharedPreferences loaded");

        editText = findViewById(R.id.editText);
        String savedPath = prefs.getString("game_path", "");
        Logger.d("Launcher", "Saved game path: " + (savedPath.isEmpty() ? "(empty)" : savedPath));
        if(savedPath == "")
            savedPath = "/storage/emulated/0/revc";
        editText.setText(savedPath);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String newText = s.toString();
                Logger.v("Launcher", "Game path changed to: " + newText);
                getSharedPreferences("app_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("game_path", newText)
                        .apply();
            }
        });

        ImageView menuButton = findViewById(R.id.menuIcon);
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Logger.d("Launcher", "Menu button clicked");
                showMenu();
            }
        });

        Button browseButton = findViewById(R.id.browseButton);
        browseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Logger.d("Launcher", "Browse button clicked, opening file picker");
                Intent intent = new Intent(LauncherActivity.this, FilepickerActivity.class);
                startActivityForResult(intent, 123);
            }
        });

        Button launchButton = findViewById(R.id.launchButton);
        launchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Logger.i("Launcher", "Launch button clicked");
                startGta();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Logger.i("Launcher", "Requesting all files access (Android 11+)");
                requestAllFilesAccess();
            } else {
                Logger.d("Launcher", "All files access already granted");
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Logger.i("Launcher", "Requesting storage permission");
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
            } else {
                Logger.d("Launcher", "Storage permission already granted");
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Window window = getWindow();
            window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }

    public static void openSettingsFromNative() {
        if (lastInstance == null) {
            Logger.w("Launcher", "openSettingsFromNative called but lastInstance is null");
            return;
        }
        Logger.i("Launcher", "Opening settings from native code");
        Intent intent = new Intent(lastInstance, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        lastInstance.startActivity(intent);
    }

    private void requestAllFilesAccess() {
        Logger.d("Launcher", "Launching all-files access settings intent");
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
        Toast.makeText(this, "Please grant 'All Files Access'", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.d("Launcher", "onRequestPermissionsResult: requestCode=" + requestCode);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Logger.i("Launcher", "Storage permission granted");
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Logger.w("Launcher", "Storage permission denied");
                Toast.makeText(this, "No permissions", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.d("Launcher", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        switch (requestCode)
        {
            case REQUEST_MANAGE_STORAGE: {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                    Logger.i("Launcher", "All files access granted");
                    Toast.makeText(this, "All Files Access granted", Toast.LENGTH_SHORT).show();
                } else {
                    Logger.w("Launcher", "All files access denied");
                    Toast.makeText(this, "All Files Access denied", Toast.LENGTH_SHORT).show();
                }
            }
            case 123:
                if (resultCode == RESULT_OK) {
                    String path = data.getStringExtra("path");
                    Logger.i("Launcher", "File picker returned path: " + path);
                    editText.setText(path);
                } else {
                    Logger.d("Launcher", "File picker cancelled or failed");
                }

        }
    }

    public void startGta() {
        Logger.i("Launcher", "startGta called");
        String gamepath = editText.getText().toString();
        Logger.d("Launcher", "Game path: " + gamepath);
        File file = new File(gamepath + "/models/gta3.img");
        if(!file.exists())
        {
            Logger.e("Launcher", "gta3.img not found at: " + file.getAbsolutePath());
            AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(this);
            dlgAlert.setMessage("An error occurred while trying to start the application."
                + System.getProperty("line.separator")
                + System.getProperty("line.separator")
                + "Error: " + "gta3.img not found. Check your file path");
                dlgAlert.setTitle("Game files not found");
                dlgAlert.setPositiveButton("Exit",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,int id) {
                    }
                });
        dlgAlert.setCancelable(false);
        dlgAlert.create().show();
        return;
        }
        Logger.i("Launcher", "Game files found, starting game with path: " + gamepath);
        setCurrentGamePath(gamepath);
        Intent intent = new Intent(LauncherActivity.this, LoadingActivity.class);
        intent.putExtra(LoadingActivity.EXTRA_GAME_PATH, gamepath);
        startActivity(intent);
    }

    static public void initEnv() {
        Logger.d("Launcher", "initEnv called");
        String gamepath = currentGamePath;
        if ((gamepath == null || gamepath.isEmpty()) && editText != null) {
            gamepath = editText.getText().toString();
            Logger.d("Launcher", "Using editText value for gamepath");
        }
        if (gamepath == null || gamepath.isEmpty()) {
            gamepath = "/storage/emulated/0/revc";
            Logger.w("Launcher", "No gamepath set, using default: " + gamepath);
        }
        Logger.i("Launcher", "Setting environment with gamepath: " + gamepath);
        setenv(gamepath);
        File file = new File(gamepath);
        Logger.d("Launcher", "Game directory exists: " + file.exists() + ", path: " + file.getAbsolutePath());
    }

    public static void setCurrentGamePath(String gamepath) {
        Logger.d("Launcher", "setCurrentGamePath: " + gamepath);
        currentGamePath = gamepath;
    }

    public static void copyMobileUiAssets(Activity activity, String gamepath) {
        Logger.i("Launcher", "copyMobileUiAssets started for path: " + gamepath);
        File outDir = new File(gamepath, "mobileui");
        if (!outDir.exists() && !outDir.mkdirs()) {
            Logger.e("Launcher", "Failed to create mobileui directory: " + outDir.getAbsolutePath());
            return;
        }

        int copied = 0;
        int skipped = 0;
        for (String assetName : MOBILE_UI_ASSETS) {
            File outFile = new File(gamepath, assetName);
            if (outFile.exists() && outFile.length() > 0) {
                Logger.v("Launcher", "Asset already exists, skipping: " + assetName);
                skipped++;
                continue;
            }

            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Logger.w("Launcher", "Failed to create asset parent: " + parent.getAbsolutePath());
                continue;
            }

            try (InputStream in = activity.getAssets().open(assetName);
                 OutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                copied++;
                Logger.d("Launcher", "Copied asset: " + assetName + " (" + outFile.length() + " bytes)");
            } catch (IOException e) {
                Logger.e("Launcher", "Failed to copy asset " + assetName + " to " + outFile.getAbsolutePath(), e);
            }
        }
        Logger.i("Launcher", "copyMobileUiAssets complete: copied=" + copied + ", skipped=" + skipped);
    }

    void showMenu() {
        PopupMenu popupMenu = new PopupMenu(LauncherActivity.this, findViewById(R.id.menuIcon));
        popupMenu.getMenuInflater().inflate(R.menu.menu_main, popupMenu.getMenu());
        popupMenu.show();


        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.action_menu) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(LauncherActivity.this);
                    builder.setMessage(R.string.about_text)
                            .setTitle(R.string.action_about);
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    return true;
                } else
                    return false;
            }
        });
    }
}

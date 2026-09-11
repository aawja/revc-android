package com.wn_klaymen1n.revc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class FilepickerActivity extends Activity {

    public static File currentDir = null;
    private ListView fileLV;
    private ArrayList<File> dirs;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.i("FilePicker", "onCreate started");
        setContentView(R.layout.filepicker);

        Button applyBtn = findViewById(R.id.select_folder);
        applyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Logger.i("FilePicker", "Folder selected: " + currentDir.getAbsolutePath());
                Intent resultIntent = new Intent();
                resultIntent.putExtra("path", currentDir.getAbsolutePath());
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });

        currentDir = Environment.getExternalStorageDirectory();
        Logger.d("FilePicker", "Initial directory: " + currentDir.getAbsolutePath());

        fileLV = findViewById(R.id.fileListView);
        DisplayFiles();
        fileLV.setOnItemClickListener((parent, view, position, id) -> {
            File selectedDir = dirs.get(position);
            if(dirs.get(position) == FileAdapter.BACK_BUTTON_MARKER)
            {
                Logger.d("FilePicker", "Back button clicked");
                goBack();
                return;
            }
            Logger.d("FilePicker", "Navigating to: " + selectedDir.getAbsolutePath());
            currentDir = selectedDir;
            DisplayFiles();
        });
    }

    private void goBack() {
        currentDir = currentDir.getParentFile();
        DisplayFiles();
    }

    private void DisplayFiles()
    {
        Logger.d("FilePicker", "DisplayFiles called for: " + currentDir.getAbsolutePath());
        File directory = currentDir;

        TextView dirText = findViewById(R.id.directoryText);
        dirText.setText(currentDir.getPath().toString());

        File[] files = directory.listFiles();
        dirs = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    dirs.add(f);
                }
            }
        }
        if (!currentDir.equals(Environment.getExternalStorageDirectory())) {
            dirs.add(0, FileAdapter.BACK_BUTTON_MARKER);
        }
        Collections.sort(dirs, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
        Logger.d("FilePicker", "Found " + dirs.size() + " directories");
        FileAdapter adapter = new FileAdapter(this, dirs);
        fileLV.setAdapter(adapter);
    }
}

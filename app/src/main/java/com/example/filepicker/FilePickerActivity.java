package com.example.filepicker;

import static me.rosuh.filepicker.config.FilePickerManager.REQUEST_CODE;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.rosuh.filepicker.config.FilePickerManager;

public class FilePickerActivity extends AppCompatActivity {

    private static final int REQUEST_MANAGE_FILES_ACCESS = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;
    private boolean isMultipleSelect = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 先检查权限，再启动文件选择
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要特殊处理
            checkMediaPermissions();
            if (!Environment.isExternalStorageManager()) {
                // 请求管理所有文件权限
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_MANAGE_FILES_ACCESS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0-10.0
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_PERMISSIONS);
            } else {
                startFilePicker();
            }
        } else {
            // Android 5.0及以下，直接启动
            startFilePicker();
        }
    }

    private void checkMediaPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 需要请求媒体权限
            List<String> permissionsNeeded = new ArrayList<>();

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO);
            }

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO);
            }

            if (!permissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this,
                        permissionsNeeded.toArray(new String[0]),
                        REQUEST_PERMISSIONS);
            } else {
                startFilePicker();
            }
        } else {
            startFilePicker();
        }
    }

    private void startFilePicker() {
        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_GET_CONTENT.equals(action) ||
                Intent.ACTION_OPEN_DOCUMENT.equals(action)) {

            isMultipleSelect = intent.hasExtra(Intent.EXTRA_ALLOW_MULTIPLE);

            if (isMultipleSelect) {
                // 选择多个文件
                FilePickerManager
                        .from(this)
                        .forResult(REQUEST_CODE);
            } else {
                // 选择单个文件
                FilePickerManager
                        .from(this)
                        .maxSelectable(1)
                        .forResult(REQUEST_CODE);
            }
        } else {
            // 默认选择单个文件
            FilePickerManager
                    .from(this)
                    .maxSelectable(1)
                    .forResult(REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MANAGE_FILES_ACCESS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    Environment.isExternalStorageManager()) {
                checkMediaPermissions();
            } else {
                Toast.makeText(this, "需要文件访问权限", Toast.LENGTH_SHORT).show();
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        } else if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                handleFileSelection();
            } else {
                // 用户取消了选择
                Toast.makeText(this, "用户取消了选择", Toast.LENGTH_SHORT).show();
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        }
    }

    private void handleFileSelection() {
        List<String> filePaths = FilePickerManager.obtainData();

        if (isMultipleSelect) {
            // 返回多个文件
            ArrayList<Uri> uris = new ArrayList<>();
            for (String path : filePaths) {
                File file = new File(path);
                Uri uri = getFileUri(file);
                uris.add(uri);
            }

            Intent result = new Intent();
            result.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            setResult(Activity.RESULT_OK, result);
        } else {
            // 返回单个文件
            String path = filePaths.get(0);
            File file = new File(path);
            Uri uri = getFileUri(file);

            Intent result = new Intent();
            result.setData(uri);
            result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            setResult(Activity.RESULT_OK, result);
        }

        finish();
    }

    private Uri getFileUri(File file) {
        try {
            // 使用 FileProvider 提供安全的 URI
            return FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    file);
        } catch (Exception e) {
            Log.e("FilePicker", "获取文件URI失败", e);

            // 回退方案：尝试使用原始方法
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                return Uri.fromFile(file);
            }
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理资源
        FilePickerManager.release();
    }
}
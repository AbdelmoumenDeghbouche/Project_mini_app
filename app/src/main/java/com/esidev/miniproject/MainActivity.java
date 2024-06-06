package com.esidev.miniproject;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.arthenica.mobileffmpeg.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_VIDEO_CAPTURE = 250;
    private static final int REQUEST_PERMISSIONS = 123;

    LinearLayout linearLayout;
    TextView debug_text;
    File videoFile;
    SwitchCompat switch_mode;
    FrameLayout frame_lyt_voice;
    FrameLayout frame_lyt_camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        System.out.println("zzzzz");
        initViews();
        switch_mode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Play audio
                // Hide the video view
                frame_lyt_camera.setVisibility(View.GONE);
            } else {
                // Stop audio
                // Show the video view
                frame_lyt_voice.setVisibility(View.VISIBLE);
            }
        });
        linearLayout.setOnClickListener(v -> new SendGetRequestTask().execute());
        frame_lyt_camera.setOnClickListener(v -> {
            // Check if the required permissions are granted
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // Request permissions if not granted
                requestPermissions(new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_PERMISSIONS);
            } else {
                // Open camera for video recording
                openCameraForVideoRecording(MainActivity.this);
            }
        });

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }
    }

    private void initViews() {
        linearLayout = findViewById(R.id.lin_lyt_connect_to_robot);
        debug_text = findViewById(R.id.debug_text);
        frame_lyt_camera = findViewById(R.id.frame_lyt_camera);
        switch_mode = (SwitchCompat) findViewById(R.id.switch_mode);
        frame_lyt_voice = (FrameLayout) findViewById(R.id.frame_lyt_voice);
    }

    private void openCameraForVideoRecording(Context context) {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        try {
            videoFile = createVideoFile();
            Uri videoUri = FileProvider.getUriForFile(this, context.getPackageName() + ".provider", videoFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_VIDEO_CAPTURE);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private File createVideoFile() throws IOException {
        String videoFileName = "video_" + System.currentTimeMillis() + ".mp4";
        File storageDir = getExternalCacheDir();
        return new File(storageDir, videoFileName);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // Permissions granted, open camera for video recording
                openCameraForVideoRecording(MainActivity.this);
            } else {
                // Permissions denied, show a message or handle accordingly
                Toast.makeText(this, "Permissions Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == RESULT_OK) {
            uploadVideoFile();
        }
    }

    private void showToastAndLog(final String message) {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            debug_text.setText(message);
        });
    }

    private void uploadVideoFile() {
        try {
            String apiUrl = "https://a31c-154-121-42-104.ngrok-free.app/data/video";

            String videoFileType = getMimeType(videoFile.getAbsolutePath());
            showToastAndLog("Video file type: " + videoFileType);

            RequestBody requestBody = RequestBody.create(MediaType.parse(videoFileType), videoFile);
            MultipartBody.Part videoFilePart = MultipartBody.Part.createFormData("frames", videoFile.getName(), requestBody);

            OkHttpClient client = new OkHttpClient();
            RequestBody formBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(videoFilePart)
                    .build();

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(formBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                private static final String TAG = "z";

                @Override
                public void onFailure(Call call, IOException e) {
                    Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("UploadError", "Error uploading video", e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    Log.d(TAG, "onResponse: " + responseBody);
                    if (response.isSuccessful()) {
                        Log.d("UploadResponse", responseBody);
                    } else {
                        Toast.makeText(MainActivity.this, "Unsuccessful upload: " + responseBody, Toast.LENGTH_LONG).show();
                        Log.e("UploadResponse", "Error code: " + response.code() + ", Error body: " + responseBody);
                    }
                }
            });
        } catch (Exception e) {
            // Log any exception that occurs and handle it gracefully
            Log.e("UploadError", "Exception during video upload", e);
            Toast.makeText(MainActivity.this, "An error occurred during video upload", Toast.LENGTH_LONG).show();
        }
    }

    private String getMimeType(String path) {
        String mimeType;
        if (path.endsWith(".mp4") || path.endsWith(".MP4")) {
            mimeType = "video/mp4";
        } else {
            mimeType = "video/*";
        }
        return mimeType;
    }

    private class SendGetRequestTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            try {
                URL url = new URL("https://a31c-154-121-42-104.ngrok-free.app/ready_to_receive");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    boolean data = jsonResponse.getBoolean("data");

                    if (data) {
                        showToastAndLog("Connected to server");
                    } else {
                        showToastAndLog("Robot is not ready yet, be patient");
                        Thread.sleep(5000);
                        new SendGetRequestTask().execute();
                    }
                } else {
                    showToastAndLog("Error: " + responseCode);
                    showToastAndLog("Retrying...");
                    new SendGetRequestTask().execute();
                }
            } catch (IOException | InterruptedException | JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        private void showToastAndLog(final String message) {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                debug_text.setText(message);
            });
        }
    }
}
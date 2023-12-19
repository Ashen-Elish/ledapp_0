package com.example.ledapptwo01;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



public class CameraActivity extends AppCompatActivity {
    public void goToMainPage(View view) { Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent); }
    private static final String TAG = "CameraActivity";

    private ExecutorService cameraExecutor;
    private Button calibrationButton;
    private FrameLayout frameLayout;

    private Bitmap baseFrame;

    // Количество пикселей в сетке
    private static final int GRID_SIZE = 8;
    // Размер квадратика сетки
    private int squareSize;
    // Массив для хранения яркости каждого квадратика сетки
    private int[][] brightnessGrid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_page);

        calibrationButton = findViewById(R.id.button);
        frameLayout = findViewById(R.id.frame);

        calibrationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendDataToESP32("0");
            }
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
        startCamera();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                    bindPreview(cameraProvider);
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Error starting camera: " + e.getMessage());
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                Bitmap bitmap = imageProxyToBitmap(image);

                if (baseFrame == null) {
                    baseFrame = convertToGrayscale(bitmap);
                    squareSize = baseFrame.getWidth() / GRID_SIZE;

                    // Инициализация массива яркости
                    brightnessGrid = new int[GRID_SIZE][GRID_SIZE];
                }

                Bitmap currentFrame = convertToGrayscale(bitmap);
                Bitmap diffFrame = getDifferenceFrame(baseFrame, currentFrame);
                int[][] gridBrightness = getGridBrightness(diffFrame);

                // Находим максимальную яркость и ее координаты
                int maxBrightness = 0;
                int maxBrightnessX = 0;
                int maxBrightnessY = 0;
                for (int i = 0; i < GRID_SIZE; i++) {
                    for (int j = 0; j < GRID_SIZE; j++) {
                        if (gridBrightness[i][j] > maxBrightness) {
                            maxBrightness = gridBrightness[i][j];
                            maxBrightnessX = i;
                            maxBrightnessY = j;
                        }
                    }
                }

                // Отправляем координаты на ESP32
                sendDataToESP32(maxBrightnessX + "," + maxBrightnessY);

                image.close();
            }
        });

        Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private Bitmap convertToGrayscale(Bitmap bitmap) {
        Bitmap grayscaleBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(grayscaleBitmap);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return grayscaleBitmap;
    }

    private Bitmap getDifferenceFrame(Bitmap baseFrame, Bitmap currentFrame) {
        Bitmap diffFrame = Bitmap.createBitmap(baseFrame.getWidth(), baseFrame.getHeight(), baseFrame.getConfig());

        int width = baseFrame.getWidth();
        int height = baseFrame.getHeight();

        int[] pixelsBase = new int[width * height];
        baseFrame.getPixels(pixelsBase, 0, width, 0, 0, width, height);

        int[] pixelsCurrent = new int[width * height];
        currentFrame.getPixels(pixelsCurrent, 0, width, 0, 0, width, height);

        int[] pixelsDiff = new int[width * height];

        for (int i = 0; i < width * height; i++) {
            int baseBrightness = getBrightness(pixelsBase[i]);
            int currentBrightness = getBrightness(pixelsCurrent[i]);

            int diffBrightness = Math.abs(currentBrightness - baseBrightness);

            pixelsDiff[i] = Color.rgb(diffBrightness, diffBrightness, diffBrightness);
        }

        diffFrame.setPixels(pixelsDiff, 0, width, 0, 0, width, height);

        return diffFrame;
    }

    private int[][] getGridBrightness(Bitmap bitmap) {
        int[][] gridBrightness = new int[GRID_SIZE][GRID_SIZE];

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                int sumBrightness = 0;

                for (int x = i * squareSize; x < (i + 1) * squareSize; x++) {
                    for (int y = j * squareSize; y < (j + 1) * squareSize; y++) {
                        sumBrightness += getBrightness(bitmap.getPixel(x, y));
                    }
                }

                int averageBrightness = sumBrightness / (squareSize * squareSize);
                gridBrightness[i][j] = averageBrightness;
            }
        }

        return gridBrightness;
    }

    private int getBrightness(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        return (r + g + b) / 3;
    }

    private OutputStream outputStream;
    private void sendDataToESP32(String data) {
        if (outputStream != null) {
            try {
                outputStream.write((data + "\n").getBytes());
            } catch (IOException e) {
                Toast.makeText(this, "Ошибка отправки данных: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Не подключено к ESP32", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
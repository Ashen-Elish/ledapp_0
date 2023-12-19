package com.example.ledapptwo01;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class DrawActivity extends AppCompatActivity {
    public void goToMainPage(View view) { Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent); }
    public void goToCameraPage(View view) { Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drawing_page);
    }
}
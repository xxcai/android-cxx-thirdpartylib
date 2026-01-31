package com.thirdlib.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.thirdlib.thirdpartylib.ThirdpartyLib;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private ThirdpartyLib thirdpartyLib;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        thirdpartyLib = new ThirdpartyLib();
        textView = findViewById(R.id.text_view);
        Button btnCompress = findViewById(R.id.btn_compress);

        textView.setText("ThirdpartyLib loaded successfully!\n" +
                         "Package: " + ThirdpartyLib.class.getPackage().getName() + "\n" +
                         "Native library: thirdpartylib");

        btnCompress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testCompression();
            }
        });
    }

    private void testCompression() {
        try {
            byte[] original = "Hello, World!".getBytes();
            byte[] compressed = thirdpartyLib.compress(original);
            if (compressed != null) {
                String msg = "Compression passed: " + original.length + " -> " + compressed.length + " bytes";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                Log.i(TAG, msg);
            } else {
                String msg = "Compression failed";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                Log.e(TAG, msg);
            }
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            Log.e(TAG, msg);
        }
    }
}

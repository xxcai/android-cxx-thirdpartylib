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

    static {
        System.loadLibrary("app");
    }

    private native int nativeAdd(int a, int b);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        thirdpartyLib = new ThirdpartyLib();
        textView = findViewById(R.id.text_view);
        textView.setText("ThirdpartyLib loaded successfully!\n" +
                         "Package: " + ThirdpartyLib.class.getPackage().getName() + "\n" +
                         "Native library: thirdpartylib");

        // 测试app java -> lib java -> lib native
        Button btnCompress = findViewById(R.id.btn_compress);
        btnCompress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testCompression();
            }
        });

        // 测试app java -> app native -> lib native
        Button btnAdd = findViewById(R.id.btn_add);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testNativeAdd();
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

    private void testNativeAdd() {
        try {
            int a = 10;
            int b = 20;
            int result = nativeAdd(a, b);
            String msg = "Native add: " + a + " + " + b + " = " + result;
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            Log.i(TAG, msg);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            Log.e(TAG, msg);
        }
    }
}

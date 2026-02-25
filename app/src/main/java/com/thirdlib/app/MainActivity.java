package com.thirdlib.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.thirdlib.thirdpartylib.ThirdpartyLib;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private ThirdpartyLib thirdpartyLib;
    private TextView textView;
    private TextView resultView;
    private ScrollView scrollView;

    static {
        System.loadLibrary("app");
    }

    private native int nativeAdd(int a, int b);
    private native String nativeTestZlib(String input);
    private native String nativeTestZlibDecompress(String input);
    private native String nativeTestOpenSSL(String input);
    private native String nativeTestCurl();
    private native String nativeTestNlohmann();
    private native String nativeTestSpdlog();
    private native String nativeTestFmt(String input);
    // mycurl 封装测试
    private native String nativeTestMycurlGet();
    private native String nativeTestMycurlPost();
    // minizip 测试
    private native String nativeTestMinizip(String dir);
    // bzip2 测试
    private native String nativeTestBzip2();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        thirdpartyLib = new ThirdpartyLib();
        textView = findViewById(R.id.text_view);
        resultView = findViewById(R.id.result_view);
        scrollView = findViewById(R.id.scroll_view);
        textView.setText("ThirdpartyLib loaded successfully!\n" +
                         "Package: " + ThirdpartyLib.class.getPackage().getName() + "\n" +
                         "Native library: thirdparty");

        // 初始化测试项列表
        List<TestItem> testItems = new ArrayList<>();
        testItems.add(new TestItem("Test Compression", this::testCompression));
        testItems.add(new TestItem("Test Native Add", this::testNativeAdd));
        testItems.add(new TestItem("Test Zlib (Prefab)", this::testZlib));
        testItems.add(new TestItem("Test OpenSSL (Prefab)", this::testOpenSSL));
        testItems.add(new TestItem("Test Curl (Direct)", this::testCurl));
        testItems.add(new TestItem("Test mycurl GET", this::testMycurlGet));
        testItems.add(new TestItem("Test mycurl POST", this::testMycurlPost));
        testItems.add(new TestItem("Test nlohmann_json", this::testNlohmann));
        testItems.add(new TestItem("Test spdlog", this::testSpdlog));
        testItems.add(new TestItem("Test fmt", this::testFmt));
        testItems.add(new TestItem("Test minizip", this::testMinizip));
        testItems.add(new TestItem("Test bzip2", this::testBzip2));

        // 设置 RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new TestAdapter(testItems));
    }

    private void logResult(String msg) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        resultView.append("[" + timestamp + "] " + msg + "\n");
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void testCompression() {
        try {
            byte[] original = "Hello, World!".getBytes();
            byte[] compressed = thirdpartyLib.compress(original);
            if (compressed != null) {
                String msg = "Compression passed: " + original.length + " -> " + compressed.length + " bytes";
                logResult(msg);
                Log.i(TAG, msg);
            } else {
                String msg = "Compression failed";
                logResult(msg);
                Log.e(TAG, msg);
            }
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            logResult(msg);
            Log.e(TAG, msg);
        }
    }

    private void testNativeAdd() {
        try {
            int a = 10;
            int b = 20;
            int result = nativeAdd(a, b);
            String msg = "Native add: " + a + " + " + b + " = " + result;
            logResult(msg);
            Log.i(TAG, msg);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            logResult(msg);
            Log.e(TAG, msg);
        }
    }

    private void testZlib() {
        try {
            String input = "Hello, Zlib! This is a test message for zlib compression.";
            String result = nativeTestZlib(input);
            String msg = "Zlib test: " + result;
            logResult(msg);
            Log.i(TAG, msg);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            logResult(msg);
            Log.e(TAG, msg);
        }
    }

    private void testOpenSSL() {
        try {
            String input = "Hello, OpenSSL!";
            String result = nativeTestOpenSSL(input);
            String msg = "OpenSSL SHA256: " + result;
            logResult(msg);
            Log.i(TAG, msg);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            logResult(msg);
            Log.e(TAG, msg);
        }
    }

    private void testCurl() {
        try {
            String result = nativeTestCurl();
            String msg = "Curl test: " + result;
            logResult(msg);
            Log.i(TAG, msg);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            logResult(msg);
            Log.e(TAG, msg);
        }
    }

    private void testNlohmann() {
        try {
            String result = nativeTestNlohmann();
            logResult(result);
            Log.i(TAG, result);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            logResult(msg);
            Log.e(TAG, msg);
        }
    }

    private void testSpdlog() {
        try {
            String result = nativeTestSpdlog();
            logResult(result);
            Log.i(TAG, result);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            logResult(msg);
            Log.e(TAG, msg);
        }
    }

    private void testFmt() {
        try {
            String input = "Hello, fmt!";
            String result = nativeTestFmt(input);
            logResult(result);
            Log.i(TAG, result);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            logResult(msg);
            Log.e(TAG, msg);
        }
    }

    // mycurl 封装测试 - HTTP GET
    private void testMycurlGet() {
        try {
            String result = nativeTestMycurlGet();
            logResult(result);
            Log.i(TAG, result);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            logResult(msg);
            Log.e(TAG, msg);
        }
    }

    // mycurl 封装测试 - HTTP POST
    private void testMycurlPost() {
        try {
            String result = nativeTestMycurlPost();
            logResult(result);
            Log.i(TAG, result);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            logResult(msg);
            Log.e(TAG, msg);
        }
    }

    // 测试 minizip
    private void testMinizip() {
        try {
            String result = nativeTestMinizip(getCacheDir().getAbsolutePath());
            logResult(result);
            Log.i(TAG, result);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            logResult(msg);
            Log.e(TAG, msg);
        }
    }

    // 测试 bzip2
    private void testBzip2() {
        try {
            String result = nativeTestBzip2();
            logResult(result);
            Log.i(TAG, result);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            logResult(msg);
            Log.e(TAG, msg);
        }
    }
}

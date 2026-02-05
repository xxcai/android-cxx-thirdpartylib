package com.thirdlib.thirdpartylib;

public class ThirdpartyLib {
    static {
        System.loadLibrary("thirdpartylib");
    }

    /**
     * 压缩数据
     * @param data 要压缩的原始数据
     * @return 压缩后的数据，失败返回null
     */
    public native byte[] compress(byte[] data);

    /**
     * 解压数据
     * @param compressedData 压缩后的数据
     * @return 解压后的原始数据，失败返回null
     */
    public native byte[] decompress(byte[] compressedData);

    /**
     * 两个整数相加
     * @param a 第一个加数
     * @param b 第二个加数
     * @return 相加结果
     */
    public native int add(int a, int b);

    /**
     * 测试OpenSSL SHA256哈希
     * @param input 输入字符串
     * @return SHA256哈希值(hex)
     */
    public native String testOpenSSL(String input);
}

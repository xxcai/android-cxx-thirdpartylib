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
}

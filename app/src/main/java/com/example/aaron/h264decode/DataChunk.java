package com.example.aaron.h264decode;

/**
 * Created by Aaron on 2016/12/8.
 */

public class DataChunk {
    byte[] data;
    int length;

    public DataChunk(byte[] data, int length) {
        this.data = data;
        this.length = length;
    }
}



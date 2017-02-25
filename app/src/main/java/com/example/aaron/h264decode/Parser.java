package com.example.aaron.h264decode;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import static com.example.aaron.h264decode.Utility.KMPMatch;

/**
 * Created by Aaron on 2017/2/25.
 */

class Parser extends Thread {
    private static final String TAG = "Parser";
    private boolean DEBUG_PARSER = false;
    private ByteBuffer RawBuffer;
    private BufferedInputStream mInputStream;
    private LinkedBlockingQueue<DataChunk> mDataList;
    private int InputStreamReadLen = 16 * 1024;
    private int mFrameRate;


    Parser(ByteBuffer byteBuffer, BufferedInputStream inputStream, LinkedBlockingQueue<DataChunk> DataList, int FrameRate) {
        RawBuffer = byteBuffer;
        mInputStream = inputStream;
        mDataList = DataList;
        mFrameRate = FrameRate;
    }

    @Override
    public void run() {
        try {
            ParserLoop();
        } catch (Exception e) {
        }
    }

    private void ParserLoop() {
        int index;
        byte[] marker0 = new byte[]{0, 0, 0, 1};
        byte[] inputBuffer = new byte[InputStreamReadLen];
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int len = mInputStream.read(inputBuffer, 0, InputStreamReadLen);
                RawBuffer.put(inputBuffer, 0, len);
            } catch (IOException e) {
                e.printStackTrace();
            }
            do {
                RawBuffer.flip();//write -> read mode (position = 0, limit = write position)
                int len = RawBuffer.limit();
                if (DEBUG_PARSER) Log.d("TCP", "limit:" + len);
                index = KMPMatch(marker0, RawBuffer.array(), 5, len);
                if (index < 0) {
                    if (DEBUG_PARSER) Log.d("TCP", "find nothing!!");
                    RawBuffer.position(len);
                    RawBuffer.limit(RawBuffer.capacity());
                    break;
                }
                index -= 4;
                if (DEBUG_PARSER)
                    Log.d(TAG, "index at:" + index);
                RawBuffer.rewind();
                byte[] frame = new byte[index];
                RawBuffer.get(frame);
                if (DEBUG_PARSER) {
                    int i;
                    for (i = 0; i < 5; i++) {
                        Log.d("Parser", "frame index " + i + "=" + frame[i]);
                    }
                }
                DataChunk data = new DataChunk(frame, index);
                try {
                    mDataList.put(data);
                    //don't fill DataList too fast
                    if (mDataList.size() > 30) {
                        try {
                            sleep(getDesireInterval());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                RawBuffer.compact();
            } while (index > 0);
        }
        Log.d("Parser", "end");
    }

    private long getDesireInterval() {
        return 1000L / mFrameRate;
    }
}
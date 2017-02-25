package com.example.aaron.h264decode;

import android.media.MediaCodec;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by Aaron on 2017/2/25.
 */

class Decoder extends Thread {
    private boolean DEBUG_CODEC = false;
    private MediaCodec mCodec;
    private long FrameRate;
    private LinkedBlockingQueue<DataChunk> mDataList;
    private long lastFrameTimeStamp;

    Decoder(MediaCodec Codec, long FrameRate, LinkedBlockingQueue<DataChunk> DataList) {
        this.mCodec = Codec;
        this.FrameRate = FrameRate;
        this.mDataList = DataList;
    }

    @Override
    public void run() {
        try {
            decodeLoop();
        } catch (Exception e) {
        }
    }

    private void decodeLoop() {
        // API < 20
        ByteBuffer[] inputBuffers = mCodec.getInputBuffers();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long startMs = System.currentTimeMillis();
        long timeoutUs = 10000;
        byte[] marker0 = new byte[]{0, 0, 0, 1};
        byte[] dummyFrame = new byte[]{0x00, 0x00, 0x01, 0x20};
        byte[] streamBuffer = null;
        DataChunk dataChunk = null;
        DataChunk dummyVideo = null;
        while (!Thread.currentThread().isInterrupted()) {
           Log.d("Decode", "get mDataList size: " + mDataList.size());
            try {
                dataChunk = mDataList.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (dataChunk == null) {
                if (dummyVideo == null) {
                    // sleep 30 ms and try again, first frame into decoder must be I frame
                    // after that, dummy frame can be used
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                } else {
                    dataChunk = dummyVideo;
                }
            }
            int startIndex = 0;
            if (dummyVideo == null) {
                dummyVideo = new DataChunk(dummyFrame, dummyFrame.length);
            }
            try {
                int inIndex = mCodec.dequeueInputBuffer(timeoutUs);
                if (inIndex >= 0) {
                    if (DEBUG_CODEC) Log.d("Decode", "get dequeueInputBuffer index:" + inIndex);
                    ByteBuffer byteBuffer = inputBuffers[inIndex];
                    byteBuffer.clear();
                    byteBuffer.put(dataChunk.data, startIndex, dataChunk.length);
                    mCodec.queueInputBuffer(inIndex, 0, dataChunk.length, 0, 0);
                    dataChunk.data = null;
                    dataChunk.length = 0;
                    dataChunk = null;
                } else {
                    if (DEBUG_CODEC)
                        Log.d("Decode", "Video codec: dequeueInputBuffer inIndex = " + inIndex);
                    continue;
                }

                int outIndex = mCodec.dequeueOutputBuffer(info, timeoutUs);
                //Frame Control
                try {
                    Thread.sleep(getAddLatencyMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (outIndex >= 0) {
                    if (DEBUG_CODEC)
                        Log.d("Decode", "get dequeueOutputBuffer index:" + inIndex);
                    boolean doRender = (info.size != 0);
                    mCodec.releaseOutputBuffer(outIndex, doRender);
                    recordCurrentTimestamp();
                } else {
                    if (DEBUG_CODEC)
                        Log.d("Decode", "fail to dequeueBuffer index = " + outIndex);
                }

            } catch (IllegalStateException e) {
                break;
            }
        }
        Log.d("Decode", "end");
    }

    private void recordCurrentTimestamp() {
        lastFrameTimeStamp = System.currentTimeMillis();
    }

    private long getAddLatencyMs() {
        if (lastFrameTimeStamp == 0)
            return 0;
        long diff = System.currentTimeMillis() - lastFrameTimeStamp;
        if (diff > getDesireInterval())
            return 0;
        else {
            long sleep = getDesireInterval() - diff;
//               Log.d("Decode", "frame control sleep for " + sleep);
            return sleep;
        }
    }

    private long getDesireInterval() {
        return 1000L / FrameRate;
    }
}
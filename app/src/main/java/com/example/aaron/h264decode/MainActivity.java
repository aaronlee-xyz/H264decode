package com.example.aaron.h264decode;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity {
    boolean DEBUG = false;
    private SurfaceView mSurface = null;
    SurfaceHolder mSurfaceHolder;
    private Thread mDecodeThread;
    private MediaCodec mCodec;
    private boolean mStopFlag = false;
    DataInputStream mInputStream;
    String FileName = "stream_chn0.h264";
    int Vide_W = 1920;
    int Video_H = 1080;
    int FrameRate = 30;
    Boolean UseSPSandPPS  =false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            /*Enter the input file path here*/
            mInputStream = new DataInputStream(new FileInputStream(new File(Environment.getExternalStorageDirectory() + "/" + FileName)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        mSurface = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceHolder = mSurface.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                try

                {
                    mCodec = MediaCodec.createDecoderByType("video/avc");
                } catch (
                        IOException e
                        )

                {
                    e.printStackTrace();
                }
                /*Assuming input file is 1920x1080 h264 stream*/
                final MediaFormat mediaformat = MediaFormat.createVideoFormat("video/avc", Vide_W, Video_H);
                /*Input PPS and SPS if necessary */
                if (UseSPSandPPS) {
                    byte[] header_sps = {0, 0, 0, 1, 103, 66, 0, 42, (byte) 149, (byte) 168, 30, 0, (byte) 137, (byte) 249, 102, (byte) 224, 32, 32, 32, 64};
                    byte[] header_pps = {0, 0, 0, 1, 104, (byte) 206, 60, (byte) 128, 0, 0, 0, 1, 6, (byte) 229, 1, (byte) 151, (byte) 128};
                    mediaformat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
                    mediaformat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
                }
                mediaformat.setInteger(MediaFormat.KEY_FRAME_RATE, FrameRate);
                /*For other fields of media format, please take a look at https://developer.android.com/reference/android/media/MediaFormat.html#KEY_MAX_INPUT_SIZE */
                mCodec.configure(
                        mediaformat,//format of input data ::decoder OR desired format of the output data:encoder
                        holder.getSurface(), //Specify a surface on which to render the output of this decoder
                        null,//Specify a crypto object to facilitate secure decryption
                        0 //For Decoding, encoding use: CONFIGURE_FLAG_ENCODE
                );

                startDecodingThread();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }


    private void startDecodingThread() {
        mCodec.start();
        mDecodeThread = new Thread(new decodeThread());
        mDecodeThread.start();
    }


    private class decodeThread implements Runnable {
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
            try {
                streamBuffer = getBytes(mInputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
            int bytes_cnt = 0;
            while (mStopFlag == false) {
                bytes_cnt = streamBuffer.length;
                if (DEBUG) Log.d("Decode", "get bytes" + bytes_cnt);

                if (bytes_cnt == 0) {
                    streamBuffer = dummyFrame;
                }

                int startIndex = 0;
                int remaining = bytes_cnt;

                while (true) {
                    if (remaining == 0 || startIndex >= remaining) {
                        break;
                    }
                    int nextframeStart = KMPMatch(marker0, streamBuffer, startIndex + 2, remaining);
                    if (nextframeStart == -1) {
                        if (DEBUG) Log.d("Decode", "find nothing");
                        nextframeStart = remaining;
                    } else {
                        if (DEBUG) Log.d("Decode", "find nalu at:" + nextframeStart);
                    }

                    int inIndex = mCodec.dequeueInputBuffer(timeoutUs);
                    if (inIndex >= 0) {
                        if (DEBUG) Log.d("Decode", "get dequeueInputBuffer index:" + inIndex);
                        ByteBuffer byteBuffer = inputBuffers[inIndex];
                        byteBuffer.clear();
                        byteBuffer.put(streamBuffer, startIndex, nextframeStart - startIndex);
                        mCodec.queueInputBuffer(inIndex, 0, nextframeStart - startIndex, 0, 0);
                        startIndex = nextframeStart;
                    } else {
                        if (DEBUG)
                            Log.d("Decode", "Video codec: dequeueInputBuffer inIndex = " + inIndex);
                        continue;
                    }

                    int outIndex = mCodec.dequeueOutputBuffer(info, timeoutUs);
                    if (outIndex >= 0) {
                        //frame control is not working in this case because no pts is available in h264 stream
                        while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                            try {
                                sleep(30);
                                if (DEBUG) Log.d("Decode", "Thread.sleep(30)");
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        if (DEBUG) Log.d("Decode", "get dequeueOutputBuffer index:" + inIndex);
                        boolean doRender = (info.size != 0);
                        mCodec.releaseOutputBuffer(outIndex, doRender);
                    } else {
                        if (DEBUG) Log.d("Decode", "fail to dequeueBuffer index = " + outIndex);
                    }
                }
            }
        }
    }

    public static byte[] getBytes(InputStream is) throws IOException {

        int len;
        int size = 1024;
        byte[] buf;

        if (is instanceof ByteArrayInputStream) {
            size = is.available();
            buf = new byte[size];
            len = is.read(buf, 0, size);
        } else {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            buf = new byte[size];
            while ((len = is.read(buf, 0, size)) != -1)
                bos.write(buf, 0, len);
            buf = bos.toByteArray();
        }
        return buf;
    }

    int KMPMatch(byte[] pattern, byte[] bytes, int start, int remain) {
        int[] lsp = computeLspTable(pattern);

        int j = 0;  // Number of chars matched in pattern
        for (int i = start; i < remain; i++) {
            while (j > 0 && bytes[i] != pattern[j]) {
                // Fall back in the pattern
                j = lsp[j - 1];  // Strictly decreasing
            }
            if (bytes[i] == pattern[j]) {
                // Next char matched, increment position
                j++;
                if (j == pattern.length)
                    return i - (j - 1);
            }
        }

        return -1;  // Not found
    }

    int[] computeLspTable(byte[] pattern) {
        int[] lsp = new int[pattern.length];
        lsp[0] = 0;  // Base case
        for (int i = 1; i < pattern.length; i++) {
            // Start by assuming we're extending the previous LSP
            int j = lsp[i - 1];
            while (j > 0 && pattern[i] != pattern[j])
                j = lsp[j - 1];
            if (pattern[i] == pattern[j])
                j++;
            lsp[i] = j;
        }
        return lsp;
    }

}

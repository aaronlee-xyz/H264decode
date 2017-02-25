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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import static com.example.aaron.h264decode.Utility.KMPMatch;
import static java.lang.Thread.sleep;

public class Player extends AppCompatActivity {
    private static final String TAG = "Player";
    SurfaceHolder mSurfaceHolder;
    private Thread mDecodeThread;
    private Thread mParserThread;
    private MediaCodec mCodec;
    BufferedInputStream mInputStream;
    SurfaceView mSurfaceView;
    private LinkedBlockingQueue<DataChunk> mDataList;
    String FileName = "stream_chn0.h264";
    int Vide_W = 1920;
    int Video_H = 1080;
    int FrameRate = 25;
    Boolean UseSPSandPPS = true;
    ByteBuffer RawBuffer = ByteBuffer.allocateDirect(1024 * 1024); //1M should be enough

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        prepareSuface();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Logger.d(TAG, "onStart");
        mDataList = new LinkedBlockingQueue<>();
        try {
            /*Enter the input file path here*/
            mInputStream = new BufferedInputStream(new FileInputStream(new File(Environment.getExternalStorageDirectory() + "/" + FileName)));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void prepareSuface(){
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                try {
                    Logger.d(TAG, "create codec");
                    mCodec = MediaCodec.createDecoderByType("video/avc");
                } catch (IOException e) {
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
        Logger.d(TAG, "startParserThread");
        mParserThread = new Parser(RawBuffer, mInputStream, mDataList, FrameRate);
        mParserThread.start();
        Logger.d(TAG, "startDecodingThread");
        mDecodeThread = new Decoder(mCodec, FrameRate, mDataList);
        mDecodeThread.start();
    }

    private void stopPlayer() {
        Logger.d(TAG, "stopPlayer");
        try {
            mParserThread.interrupt();
            mParserThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            mDecodeThread.interrupt();
            mDecodeThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (mCodec != null) {
            mCodec.stop();
            mCodec.release();
        }
        if (RawBuffer != null)
            RawBuffer.clear();
        try {
            if (mInputStream != null){
                mInputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void releasePlayer() {
        Logger.d(TAG, "releasePlayer");
        if (mDataList != null) {
            mDataList.clear();
            mDataList = null;
        }
    }

    @Override
    protected void onPause() {
        Logger.d(TAG, "onPause");
        super.onPause();
        stopPlayer();
        releasePlayer();
    }
}

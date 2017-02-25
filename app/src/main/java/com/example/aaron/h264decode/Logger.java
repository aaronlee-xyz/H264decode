package com.example.aaron.h264decode;

import android.util.Log;

/**
 * Created by Aaron on 2017/1/14.
 */

public class Logger {

    public static void d(String Tag, String msg) {
        if (BuildConfig.DEBUG)
            Log.d(Tag, msg);
    }

    public static void e(String Tag, String msg) {
            Log.e(Tag, msg); //always allow error log
    }

    public static void i(String Tag, String msg) {
        if (BuildConfig.DEBUG)
            Log.i(Tag, msg);
    }

    public static void v(String Tag, String msg) {
        if (BuildConfig.DEBUG)
            Log.v(Tag, msg);
    }

}

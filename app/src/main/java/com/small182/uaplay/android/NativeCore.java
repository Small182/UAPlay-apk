package com.small182.uaplay.android;

/** JNI API for the actual UAPlay C catalog, sources, IPTV and saved library. */
public final class NativeCore {
    static { System.loadLibrary("uaplay_core"); }
    private NativeCore() {}
    public static native void init();
    public static native void language(boolean english);
    public static native String catalog(String category,String query,int page,int sort);
    public static native String details(int id,boolean series,String title,String original,String year);
    public static native String sources(int id,boolean series,String title,String original,String year,int provider);
    public static native String episodes(String title,String url);
    public static native String resolve(String title,String url);
    public static native String openMovy(String url,int bandwidth);
    public static native void closeMovy();
    public static native void setKey(String apiKey);
    public static native boolean hasKey();
    public static native boolean isFavorite(int id,boolean series,String title,String original,String year);
    public static native void toggleFavorite(int id,boolean series,String title,String original,String year);
    public static native String library(int mode);
    public static native void progress(int id,boolean series,String title,String original,String year,int season,int episode,double seconds);
    public static native double resume(int id,boolean series,String title,String original,String year);
    public static native String iptv(int country,int start,int size);
}

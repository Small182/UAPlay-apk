package com.small182.uaplay.android;

import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTPS transport used by the original UAPlay C catalog via JNI. */
public final class NetworkBridge {
    private NetworkBridge() {}
    public static final class Result {
        public final byte[] body;
        public final int status;
        public final String effectiveUrl;
        public Result(byte[] body,int status,String effectiveUrl) {
            this.body=body;this.status=status;this.effectiveUrl=effectiveUrl;
        }
    }
    public static Result fetch(String address,String referer,String userAgent,
                               String post,String headerLines,int connectionSeconds,
                               int readSeconds,boolean redirects) {
        HttpURLConnection conn=null;
        try {
            URL u=new URL(address);
            String scheme=u.getProtocol();
            if(!"https".equalsIgnoreCase(scheme) &&
               !("http".equalsIgnoreCase(scheme) &&
                 ("127.0.0.1".equals(u.getHost())||"localhost".equalsIgnoreCase(u.getHost())))) {
                // IPTV can use HTTP, but the catalog and provider scrapers require HTTPS.
                if(!"http".equalsIgnoreCase(scheme))throw new IllegalArgumentException("Invalid scheme");
            }
            conn=(HttpURLConnection)u.openConnection();
            conn.setConnectTimeout(Math.max(1,connectionSeconds)*1000);
            conn.setReadTimeout(Math.max(1,readSeconds)*1000);
            conn.setInstanceFollowRedirects(redirects);
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent",userAgent.isEmpty()?"UAPlay/0.6 Android":userAgent);
            if(!referer.isEmpty())conn.setRequestProperty("Referer",referer);
            if(headerLines!=null) {
                for(String h:headerLines.split("\n")){
                    int colon=h.indexOf(':');
                    if(colon>0 && colon<h.length()-1)
                        conn.setRequestProperty(h.substring(0,colon).trim(),h.substring(colon+1).trim());
                }
            }
            if(post!=null && !post.isEmpty()) {
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                byte[] bytes=post.getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
                conn.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
            }
            int status=conn.getResponseCode();
            InputStream input=status>=400?conn.getErrorStream():conn.getInputStream();
            if(input==null)return new Result(new byte[0],status,conn.getURL().toString());
            ByteArrayOutputStream result=new ByteArrayOutputStream();
            try(InputStream body=input) {
                byte[] buf=new byte[32768];int n;
                while((n=body.read(buf))!=-1) {
                    if(result.size()+n>32*1024*1024)throw new java.io.IOException("Response too large");
                    result.write(buf,0,n);
                }
            }
            return new Result(result.toByteArray(),status,conn.getURL().toString());
        }catch(Exception ex){
            Log.w("UAPlayNetwork","Request error: "+ex.getClass().getSimpleName()+": "+ex.getMessage());
            return new Result(null,0,address);
        }finally{if(conn!=null)conn.disconnect();}
    }
}

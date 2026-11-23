package com.small182.uaplay.android;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.NoOpCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.exoplayer.hls.offline.HlsDownloader;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persistent download queue. HLS uses Media3's real segment downloader and
 * caches all playlists/segments. Direct MP4s are stored in app-private files.
 * In-process pause/cancel is supported; unfinished entries can be retried.
 */
@UnstableApi
public final class OfflineManager {
    private static OfflineManager instance;
    private final Context context;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Map<String,Item> items=new java.util.LinkedHashMap<>();
    private final SimpleCache cache;
    private volatile HlsDownloader downloader;
    private volatile String currentId="";
    private volatile boolean cancelCurrent=false;
    private volatile boolean pausedCurrent=false;
    private final File directDir;
    private final File index;
    public interface Listener {void updated();}
    private Listener listener;

    public static final class Item {
        public String id,url,title,referer,path,state,error;
        public long bytes,total;
        public boolean hls;
        Item(String id,String url,String title,String referer,boolean hls) {
            this.id=id;this.url=url;this.title=title;this.referer=referer;this.hls=hls;
            this.path="";this.state="queued";this.error="";this.total=-1;
        }
        JSONObject toJson() {
            JSONObject v=new JSONObject();
            try{
                v.put("id",id);v.put("url",url);v.put("title",title);v.put("referer",referer);
                v.put("path",path);v.put("state",state);v.put("error",error);v.put("hls",hls);
                v.put("bytes",bytes);v.put("total",total);
            }catch(Exception ignored){}
            return v;
        }
        static Item fromJson(JSONObject o){
            Item d=new Item(o.optString("id"),o.optString("url"),o.optString("title"),
                o.optString("referer"),o.optBoolean("hls"));
            d.path=o.optString("path");d.state=o.optString("state","queued");
            if("downloading".equals(d.state)||"queued".equals(d.state))d.state="paused";
            d.error=o.optString("error");d.bytes=o.optLong("bytes");d.total=o.optLong("total",-1);
            return d;
        }
        public String description() {
            String status;
            switch(state) {
                case "done":status="✓";break;
                case "downloading":status="↓";break;
                case "paused":status="Ⅱ";break;
                case "error":status="!";break;
                default:status="…";break;
            }
            String size=bytes>0?String.format(java.util.Locale.US,"%.1f MB",bytes/1048576.0):"";
            String percent=total>0?String.format(java.util.Locale.US," %.0f%%",100.0*bytes/total):"";
            return status+" "+title+"\n"+size+percent+(error.isEmpty()?"":" · "+error);
        }
    }
    public static synchronized OfflineManager get(Context ctx) {
        if(instance==null)instance=new OfflineManager(ctx.getApplicationContext());
        return instance;
    }
    private OfflineManager(Context context){
        this.context=context;
        File directory=new File(context.getFilesDir(),"uaplay/downloads");
        directDir=new File(directory,"files");directDir.mkdirs();index=new File(directory,"index.json");
        File hls=new File(directory,"hls");hls.mkdirs();
        cache=new SimpleCache(hls,new NoOpCacheEvictor(),new StandaloneDatabaseProvider(context));
        try{
            if(index.isFile()){
                String json=new String(java.nio.file.Files.readAllBytes(index.toPath()),java.nio.charset.StandardCharsets.UTF_8);
                JSONArray list=new JSONArray(json);
                for(int i=0;i<list.length();i++){
                    JSONObject record=list.optJSONObject(i);if(record==null)continue;
                    Item item=Item.fromJson(record);if(!item.id.isEmpty())items.put(item.id,item);
                }
            }
        }catch(Exception ignored){}
    }
    private synchronized void save(){
        JSONArray array=new JSONArray();
        for(Item item:items.values())array.put(item.toJson());
        try(FileOutputStream file=new FileOutputStream(index)){
            file.write(array.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }catch(Exception ignored){}
        if(listener!=null)listener.updated();
    }
    public synchronized void setListener(Listener listener){this.listener=listener;}
    public synchronized List<Item> all(){return new ArrayList<>(items.values());}
    public synchronized boolean downloaded(String url){
        for(Item x:items.values())if(x.url.equals(url)&&"done".equals(x.state))return true;
        return false;
    }
    public synchronized Uri localUri(String url){
        for(Item x:items.values()){
            if(x.url.equals(url)&&"done".equals(x.state)&&!x.hls&&!x.path.isEmpty()){
                File f=new File(x.path);
                if(f.isFile())return Uri.fromFile(f);
            }
        }
        return null;
    }
    private static String idOf(String url){return Integer.toHexString(url.hashCode())+"-"+Integer.toHexString(url.length());}
    public synchronized void add(String url,String referer,String title){
        boolean hls=url.toLowerCase(java.util.Locale.ROOT).contains(".m3u8");
        String id=idOf(url);
        Item existing=items.get(id);
        if(existing!=null&&"done".equals(existing.state))return;
        if(existing==null){existing=new Item(id,url,title,referer,hls);items.put(id,existing);}
        else {existing.state="queued";existing.error="";}
        save();Item task=existing;
        worker.execute(()->runDownload(task));
    }
    public synchronized void resume(String id){
        Item item=items.get(id);if(item==null||"done".equals(item.state))return;
        item.state="queued";item.error="";save();worker.execute(()->runDownload(item));
    }
    public synchronized void pause(String id){
        Item item=items.get(id);if(item==null)return;
        item.state="paused";save();
        if(id.equals(currentId)){
            pausedCurrent=true;cancelCurrent=true;
            if(downloader!=null)downloader.cancel();
        }
    }
    public synchronized void remove(String id){
        Item item=items.remove(id);
        if(item==null)return;
        if(id.equals(currentId)){cancelCurrent=true;if(downloader!=null)downloader.cancel();}
        if(item.path!=null&&!item.path.isEmpty())new File(item.path).delete();
        save();
    }
    private void progress(Item item,long bytes,long total){
        synchronized(this){item.bytes=bytes;item.total=total;save();}
    }
    private void runDownload(Item item){
        synchronized(this){
            if(!items.containsKey(item.id)||!"queued".equals(item.state))return;
            item.state="downloading";currentId=item.id;cancelCurrent=false;pausedCurrent=false;save();
        }
        try{
            if(item.hls)downloadHls(item);else downloadDirect(item);
            synchronized(this){
                if(items.containsKey(item.id)&&!cancelCurrent&&!"paused".equals(item.state)){
                    item.state="done";item.error="";save();
                }
            }
        }catch(Exception ex){
            synchronized(this){
                if(items.containsKey(item.id)&&!"paused".equals(item.state)&&!cancelCurrent){
                    item.state="error";
                    item.error=ex.getMessage()==null?"Network error":ex.getMessage();
                    if(item.error.length()>95)item.error=item.error.substring(0,95);
                    save();
                }
            }
        }finally{currentId="";downloader=null;}
    }
    private DefaultHttpDataSource.Factory http(Item item){
        DefaultHttpDataSource.Factory f=new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true).setUserAgent("Mozilla/5.0 (Android TV; UAPlay)");
        if(item.referer!=null&&!item.referer.isEmpty()){
            Map<String,String> headers=new HashMap<>();headers.put("Referer",item.referer);
            f.setDefaultRequestProperties(headers);
        }
        return f;
    }
    private void downloadHls(Item item)throws Exception{
        CacheDataSource.Factory cf=new CacheDataSource.Factory()
            .setCache(cache).setUpstreamDataSourceFactory(http(item));
        MediaItem media=new MediaItem.Builder().setUri(item.url)
            .setMimeType(MimeTypes.APPLICATION_M3U8).build();
        HlsDownloader task=new HlsDownloader.Factory(cf).create(media);
        downloader=task;
        task.download((total,downloaded,percent)->{
            if(cancelCurrent)task.cancel();
            // Throttle persistence; synchronized writes on every segment are not necessary.
            if(downloaded-item.bytes>1024*1024 || total>0 && downloaded>=total)
                progress(item,downloaded,total);
        });
        if(cancelCurrent)throw new InterruptedException("Canceled");
        synchronized(this){item.bytes=Math.max(item.bytes,1);item.total=item.bytes;save();}
    }
    private void downloadDirect(Item item)throws Exception{
        File file=new File(directDir,item.id+".mp4");
        long prior=file.exists()?file.length():0;
        HttpURLConnection conn=null;
        try{
            conn=(HttpURLConnection)new URL(item.url).openConnection();
            conn.setConnectTimeout(12000);conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent","Mozilla/5.0 (Android TV; UAPlay)");
            if(!item.referer.isEmpty())conn.setRequestProperty("Referer",item.referer);
            if(prior>0)conn.setRequestProperty("Range","bytes="+prior+"-");
            int status=conn.getResponseCode();
            if(status!=200&&status!=206)throw new java.io.IOException("HTTP "+status);
            boolean append=status==206&&prior>0;
            if(!append)prior=0;
            long length=conn.getContentLengthLong(),total=length>0?length+prior:-1;
            long count=prior;
            try(InputStream in=conn.getInputStream();FileOutputStream out=new FileOutputStream(file,append)){
                byte[] buf=new byte[65536];int n;
                while((n=in.read(buf))!=-1){
                    if(cancelCurrent||Thread.currentThread().isInterrupted())throw new InterruptedException("Canceled");
                    out.write(buf,0,n);count+=n;
                    if(count-item.bytes>=1024*512)progress(item,count,total);
                }
            }
            if(count==0)throw new java.io.IOException("Empty file");
            synchronized(this){item.path=file.getAbsolutePath();item.bytes=count;item.total=count;save();}
        }finally{if(conn!=null)conn.disconnect();}
    }
    public DataSource.Factory dataSourceFor(String url,Map<String,String> requestHeaders){
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true).setUserAgent("Mozilla/5.0 (Android TV; UAPlay)");
        http.setDefaultRequestProperties(requestHeaders);
        if(downloaded(url)){
            return new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(http);
        }
        return http;
    }
}

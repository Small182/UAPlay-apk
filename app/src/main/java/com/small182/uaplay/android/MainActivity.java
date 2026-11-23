package com.small182.uaplay.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UAPlay Android / Android TV client for the original C catalog + provider engine.
 * This is NOT the old demo VideoView application. ExoPlayer supports DASH/HLS
 * (HLS module bundled), native source resolution and TV remote navigation.
 */
public class MainActivity extends Activity {
    private static final int BG=Color.rgb(11,14,20);
    private static final int CARD=Color.rgb(28,34,45);
    private static final int FOCUS=Color.rgb(32,181,165);
    private static final int WHITE=Color.rgb(242,244,249);
    private static final int GREY=Color.rgb(165,180,194);
    private static final String PREFS="uaplay_android";
    private static final String MODE="display_mode";
    private static final int START=0,BROWSE=1,DETAIL=2,SOURCES=3,EPISODES=4,PLAYER=5,SETTINGS=6,IPTV=7,LIBRARY=8;
    private static final String[][] CATEGORIES={
        {"top","Головна","Home"},
        {"films","Фільми","Movies"},
        {"series","Серіали","Series"},
        {"cartoons","Мультфільми","Cartoons"},
        {"cartoon_series","Мультсеріали","Cartoon series"},
        {"anime","Аніме","Anime"},
        {"genre_28","Бойовики","Action"},
        {"genre_35","Комедії","Comedies"},
        {"genre_27","Жахи","Horror"}
    };
    private static final String[] PROVIDER_UA={"Усі","UAFlix","UASerials","Eneyida","UAFilm","UAKino","Mikai","AnimeON","Movy"};
    private static final int[] PROVIDER_ID={0,1,2,3,4,5,6,7,9};

    private SharedPreferences prefs;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService core=Executors.newSingleThreadExecutor();
    private final ExecutorService pictures=Executors.newFixedThreadPool(3);
    private final android.util.LruCache<String,Bitmap> posterCache=new android.util.LruCache<String,Bitmap>(50){
        @Override protected int sizeOf(String key,Bitmap value){return 1;}
    };
    private int page=START, generation=0, currentPage=1, sortMode=0;
    private boolean morePages=false;
    private String category="top",query="";
    private LinearLayout screen;
    private LinearLayout results;
    private Button firstFocus;
    private JSONObject selectedMovie;
    private JSONObject selectedSource;
    private JSONArray episodes=new JSONArray(), audioOptions=new JSONArray(), subtitleOptions=new JSONArray();
    private int currentEpisode=-1, episodeSeason=-1, episodeShown=0;
    private ExoPlayer player;
    private PlayerView playerView;
    private FrameLayout videoRoot;
    private LinearLayout videoBar;
    private String playingUrl="", playingReferer="";
    private boolean playingMovy=false,playingIptv=false;
    private int iptvCountry=-1,iptvOffset=0,iptvTotal=0;
    private String playingName="";
    private Runnable saveProgress;

    private boolean uk(){return prefs.getString("lang","uk").equals("uk");}
    private String s(String ua,String en){return uk()?ua:en;}
    private boolean tv(){return prefs!=null && "tv".equals(prefs.getString(MODE,"phone"));}
    private int px(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private boolean hardwareTv(){
        UiModeManager mgr=(UiModeManager)getSystemService(UI_MODE_SERVICE);
        return mgr!=null&&mgr.getCurrentModeType()==Configuration.UI_MODE_TYPE_TELEVISION;
    }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        copyOfflineCatalogs();
        NativeCore.init();
        NativeCore.language(!uk());
        orientation();
        if(!prefs.contains(MODE))firstRun();else browse("top");
    }
    private void copyOfflineCatalogs(){
        File dir=new File(getFilesDir(),"uaplay/offline");
        if(!dir.exists())dir.mkdirs();
        for(String f:new String[]{"movies.json","series.json","top.json"}){
            File target=new File(dir,f);
            if(target.exists()&&target.length()>0)continue;
            try(InputStream in=getAssets().open("offline/"+f);FileOutputStream out=new FileOutputStream(target)){
                byte[] bytes=new byte[8192];int n;
                while((n=in.read(bytes))>0)out.write(bytes,0,n);
            }catch(Exception ex){android.util.Log.w("UAPlay","Cannot copy offline catalog",ex);}
        }
    }
    private void orientation(){
        setRequestedOrientation(tv()?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }
    private GradientDrawable background(int color,int border){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(px(13));
        if(border!=0)d.setStroke(px(2),border);
        return d;
    }
    private TextView label(String text,int size,int color){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);
        t.setLineSpacing(px(3),1.05f);return t;
    }
    private Button btn(String value,Runnable onPress){
        Button b=new Button(this);b.setAllCaps(false);b.setText(value);
        b.setTextSize(tv()?19:15);b.setTextColor(WHITE);
        b.setPadding(px(12),px(tv()?11:7),px(12),px(tv()?11:7));
        b.setBackground(background(CARD,0));b.setFocusable(true);
        b.setOnClickListener(v->onPress.run());
        b.setOnFocusChangeListener((v,hasFocus)->{
            b.setBackground(background(hasFocus?FOCUS:CARD,hasFocus?WHITE:0));
            b.setTextColor(hasFocus?BG:WHITE);
        });return b;
    }
    private void buttonRow(LinearLayout parent,Button b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=px(tv()?12:8);parent.addView(b,p);
        if(firstFocus==null)firstFocus=b;
    }
    private void space(LinearLayout parent,int dp){
        View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,px(dp)));
    }
    private LinearLayout content(int nextPage){
        generation++;page=nextPage;firstFocus=null;
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);sc.setFillViewport(true);
        LinearLayout container=new LinearLayout(this);container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(px(tv()?52:16),px(tv()?30:22),px(tv()?52:16),px(40));
        sc.addView(container,new ScrollView.LayoutParams(-1,-2));
        setContentView(sc);screen=container;
        return container;
    }
    private void focusFirst(){
        if((tv()||hardwareTv())&&firstFocus!=null)firstFocus.requestFocus();
    }
    private void title(LinearLayout parent,String text){
        TextView t=label(text,tv()?36:27,WHITE);t.setTypeface(null,1);parent.addView(t);space(parent,13);
    }
    private void note(LinearLayout parent,String text){
        parent.addView(label(text,tv()?18:14,GREY));space(parent,12);
    }
    private void busy(LinearLayout parent){
        ProgressBar progress=new ProgressBar(this);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(px(36),px(36));
        p.gravity=Gravity.CENTER_HORIZONTAL;parent.addView(progress,p);
        space(parent,18);
    }
    private void async(java.util.concurrent.Callable<String> job,java.util.function.Consumer<JSONObject> result,int expected){
        core.submit(()->{
            JSONObject value;
            try{value=new JSONObject(job.call());}
            catch(Exception ex){
                value=new JSONObject();
                try{value.put("error",ex.getMessage()==null?"Unknown native error":ex.getMessage());}
                catch(Exception ignored){}
            }
            final JSONObject done=value;
            runOnUiThread(()->{if(generation==expected&&!isFinishing())result.accept(done);});
        });
    }
    private String titleOf(JSONObject movie){return movie.optString("title",s("Без назви","Untitled"));}
    private int idOf(JSONObject m){return m.optInt("id");}
    private boolean seriesOf(JSONObject m){return m.optBoolean("series");}
    private String originalOf(JSONObject m){return m.optString("original");}
    private String yearOf(JSONObject m){return m.optString("year");}

    private void firstRun(){
        LinearLayout l=content(START);
        l.addView(label("UAPlay",tv()?48:35,FOCUS));space(l,28);
        title(l,s("Оберіть спосіб керування","Choose control mode"));
        note(l,s("Режим можна будь-коли змінити в налаштуваннях.",
            "You can switch mode later in Settings."));
        Button phone=btn(s("Телефон / планшет\nСенсорне керування",
                          "Phone / tablet\nTouch controls"),()->setMode("phone"));
        Button television=btn(s("Телевізор\nПульт, стрілки, OK та Назад",
                               "Television\nRemote, arrows, OK and Back"),()->setMode("tv"));
        buttonRow(l,phone);buttonRow(l,television);
        if(hardwareTv())television.requestFocus();else phone.requestFocus();
    }
    private void setMode(String mode){
        prefs.edit().putString(MODE,mode).apply();orientation();
        Toast.makeText(this,s("Режим збережено","Mode saved"),Toast.LENGTH_SHORT).show();
        browse("top");
    }
    private void tabs(LinearLayout parent){
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        for(String[] cat:CATEGORIES){
            Button b=btn(uk()?cat[1]:cat[2],()->browse(cat[0]));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=px(8);
            row.addView(b,lp);if(firstFocus==null)firstFocus=b;
        }
        Button saved=btn(s("Обране","Saved"),this::showFavorites);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.rightMargin=px(8);row.addView(saved,p);
        Button iptv=btn("IPTV",()->showIptv(-1,0));row.addView(iptv,p);
        Button settings=btn(s("Налаштування","Settings"),this::settings);row.addView(settings,p);
        scroll.addView(row);parent.addView(scroll);space(parent,15);
    }
    private void browse(String selectedCategory){
        category=selectedCategory;query="";currentPage=1;morePages=false;sortMode=0;
        LinearLayout l=content(BROWSE);l.addView(label("UAPlay",tv()?40:30,FOCUS));
        space(l,12);tabs(l);
        LinearLayout search=new LinearLayout(this);search.setOrientation(LinearLayout.HORIZONTAL);
        EditText searchText=new EditText(this);searchText.setSingleLine(true);
        searchText.setTextSize(tv()?19:16);searchText.setTextColor(WHITE);
        searchText.setHintTextColor(GREY);searchText.setHint(s("Пошук фільмів та серіалів","Search movies and series"));
        LinearLayout.LayoutParams edit=new LinearLayout.LayoutParams(0,-2,1);search.addView(searchText,edit);
        Button find=btn(s("Знайти","Search"),()->{
            query=searchText.getText().toString().trim();
            currentPage=1;showBrowseResults();});
        search.addView(find);searchText.setOnEditorActionListener((view,id,event)->{
            query=searchText.getText().toString().trim();currentPage=1;showBrowseResults();return true;
        });
        l.addView(search);space(l,12);
        LinearLayout sorting=new LinearLayout(this);sorting.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<3;i++){
            final int index=i;
            String[] ua={"Популярне","Рейтинг","Новинки"};
            String[] en={"Popular","Top rated","Newest"};
            Button b=btn(uk()?ua[i]:en[i],()->{sortMode=index;currentPage=1;showBrowseResults();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.rightMargin=px(6);sorting.addView(b,p);
        }
        l.addView(sorting);space(l,15);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);
        l.addView(results);
        showBrowseResults();focusFirst();
    }
    private void showBrowseResults(){
        if(results==null||page!=BROWSE)return;
        results.removeAllViews();final int ticket=++generation;
        busy(results);
        final String currentCategory=category, currentQuery=query;final int pageToGet=currentPage,sorted=sortMode;
        async(()->NativeCore.catalog(currentCategory,currentQuery,pageToGet,sorted),response->{
            if(page!=BROWSE)return;results.removeAllViews();
            JSONArray items=response.optJSONArray("items");
            String error=response.optString("error","");
            morePages=response.optBoolean("more");
            if(items==null||items.length()==0){
                note(results,error.isEmpty()?s("Нічого не знайдено","Nothing found"):error);
                if(!NativeCore.hasKey())buttonRow(results,btn(s("Додати ключ TMDB","Add TMDB key"),this::showKeyDialog));
                return;
            }
            if(!error.isEmpty())note(results,error);
            addMovieGrid(results,items);
            if(morePages){
                buttonRow(results,btn(s("Наступна сторінка →","Next page →"),()->{
                    currentPage++;showBrowseResults();
                }));
            } else if(!NativeCore.hasKey()) {
                note(results,s("Показано вбудований офлайн-каталог. Для оновлень налаштуйте TMDB.",
                               "Built-in offline catalog. Configure TMDB to fetch live lists."));
            }
        },ticket);
    }
    private void poster(ImageView image,String posterPath){
        if(posterPath==null||posterPath.isEmpty())return;
        String url=posterPath.startsWith("http")?posterPath:"https://image.tmdb.org/t/p/w342"+posterPath;
        Bitmap found=posterCache.get(url);if(found!=null){image.setImageBitmap(found);return;}
        pictures.submit(()->{
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL(url).openConnection();
                connection.setConnectTimeout(6000);connection.setReadTimeout(9000);
                try(InputStream in=connection.getInputStream()){
                    Bitmap bmp=BitmapFactory.decodeStream(in);
                    if(bmp!=null){posterCache.put(url,bmp);
                        runOnUiThread(()->{if(url.equals(image.getTag()))image.setImageBitmap(bmp);});}
                }
            }catch(Exception ignored){}
            finally{if(connection!=null)connection.disconnect();}
        });
        image.setTag(url);
    }
    private void addMovieGrid(LinearLayout parent,JSONArray items){
        GridLayout grid=new GridLayout(this);int columns=tv()?5:2;grid.setColumnCount(columns);
        for(int i=0;i<items.length();i++){
            JSONObject movie=items.optJSONObject(i);if(movie==null)continue;
            LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);
            item.setBackground(background(CARD,0));item.setPadding(px(5),px(5),px(5),px(8));
            item.setFocusable(true);item.setClickable(true);
            item.setOnClickListener(v->details(movie));
            item.setOnFocusChangeListener((v,on)->{item.setBackground(background(on?FOCUS:CARD,on?WHITE:0));item.setScaleX(on?1.04f:1);item.setScaleY(on?1.04f:1);});
            ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackgroundColor(Color.rgb(38,47,58));
            int width=tv()?150:148,height=tv()?214:213;
            item.addView(img,new LinearLayout.LayoutParams(-1,px(height)));
            poster(img,movie.optString("poster"));
            TextView name=label(titleOf(movie),tv()?14:13,WHITE);
            name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.addView(name);
            TextView rating=label(movie.optString("year")+"   ★ "+String.format(Locale.US,"%.1f",movie.optDouble("rating")),tv()?12:11,GREY);
            item.addView(rating);
            GridLayout.LayoutParams gp=new GridLayout.LayoutParams();
            gp.columnSpec=GridLayout.spec(i%columns,1f);gp.rowSpec=GridLayout.spec(i/columns);
            gp.width=0;gp.setGravity(Gravity.FILL_HORIZONTAL);
            gp.setMargins(px(3),px(3),px(3),px(7));
            grid.addView(item,gp);
        }
        parent.addView(grid,new LinearLayout.LayoutParams(-1,-2));
    }
    private void details(JSONObject movie){
        selectedMovie=movie;LinearLayout l=content(DETAIL);
        buttonRow(l,btn(s("← Каталог","← Catalog"),()->browse(category)));
        title(l,titleOf(movie));
        TextView info=label(movie.optString("year")+"    ★ "+
            String.format(Locale.US,"%.1f",movie.optDouble("rating")),tv()?20:16,FOCUS);
        l.addView(info);space(l,12);
        ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        l.addView(img,new LinearLayout.LayoutParams(-1,px(tv()?230:190)));
        poster(img,movie.optString("poster"));space(l,15);
        note(l,movie.optString("overview"));
        Button watch=btn(s("▶ Дивитися / Джерела","▶ Watch / Sources"),()->showSources(0));buttonRow(l,watch);
        boolean saved=NativeCore.isFavorite(idOf(movie),seriesOf(movie),titleOf(movie),originalOf(movie),yearOf(movie));
        buttonRow(l,btn(saved?s("★ Прибрати з обраного","★ Remove from favorites"):s("☆ Додати до обраного","☆ Add to favorites"),()->{
            NativeCore.toggleFavorite(idOf(movie),seriesOf(movie),titleOf(movie),originalOf(movie),yearOf(movie));
            details(movie);
        }));
        focusFirst();
        final int ticket=generation;
        async(()->NativeCore.details(idOf(movie),seriesOf(movie),titleOf(movie),originalOf(movie),yearOf(movie)),response->{
            JSONObject more=response.optJSONObject("movie");
            if(more!=null){
                String genre=response.optString("genre");
                String runtime=more.optInt("runtime")>0?" · "+more.optInt("runtime")+" "+s("хв","min"):"";
                if(!genre.isEmpty()||!runtime.isEmpty())info.setText(movie.optString("year")+"    ★ "+String.format(Locale.US,"%.1f",movie.optDouble("rating"))+" · "+genre+runtime);
            }
        },ticket);
    }
    private void showSources(int provider){
        if(selectedMovie==null)return;
        LinearLayout l=content(SOURCES);buttonRow(l,btn(s("← До опису","← Details"),()->details(selectedMovie)));
        title(l,titleOf(selectedMovie));
        note(l,s("Виберіть джерело відео","Choose a video provider"));
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<PROVIDER_ID.length;i++){
            final int val=PROVIDER_ID[i];
            Button b=btn(PROVIDER_UA[i],()->showSources(val));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.rightMargin=px(8);row.addView(b,p);
        }
        scroll.addView(row);l.addView(scroll);space(l,18);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);l.addView(list);
        busy(list);final int ticket=generation;
        JSONObject m=selectedMovie;
        async(()->NativeCore.sources(idOf(m),seriesOf(m),titleOf(m),originalOf(m),yearOf(m),provider),response->{
            list.removeAllViews();JSONArray sources=response.optJSONArray("items");
            if(sources==null||sources.length()==0){note(list,response.optString("error",s("Джерела не знайдено","No sources")));return;}
            note(list,s("Знайдено джерел: ","Sources found: ")+sources.length());
            for(int i=0;i<sources.length();i++){
                JSONObject source=sources.optJSONObject(i);if(source==null)continue;
                String link=source.optString("url");
                buttonRow(list,btn(source.optString("title",link),()->{
                    selectedSource=source;
                    if(seriesOf(selectedMovie))loadEpisodes();
                    else resolveAndPlay(source);
                }));
            }
            focusFirst();
        },ticket);
    }
    private void loadEpisodes(){
        if(selectedSource==null)return;
        LinearLayout l=content(EPISODES);
        buttonRow(l,btn(s("← Джерела","← Sources"),()->showSources(0)));
        title(l,titleOf(selectedMovie));
        note(l,s("Завантаження сезонів та серій...","Loading seasons and episodes..."));
        final int ticket=generation;JSONObject source=selectedSource;
        async(()->NativeCore.episodes(source.optString("title"),source.optString("url")),response->{
            episodes=response.optJSONArray("items");
            if(episodes==null||episodes.length()==0){
                note(l,response.optString("error",s("Серій не знайдено. Спробую пряме джерело.","No episodes. Trying direct source.")));
                buttonRow(l,btn(s("▶ Відкрити джерело","▶ Open source"),()->resolveAndPlay(source)));
                return;
            }
            episodeSeason=-1;episodeShown=0;showEpisodesList();
        },ticket);
    }
    private void showEpisodesList(){
        LinearLayout l=content(EPISODES);
        buttonRow(l,btn(s("← Джерела","← Sources"),()->showSources(0)));
        title(l,titleOf(selectedMovie));
        if(episodes==null)return;
        Set<Integer> seasons=new HashSet<>();
        for(int i=0;i<episodes.length();i++){
            JSONObject ep=episodes.optJSONObject(i);if(ep!=null)seasons.add(ep.optInt("season"));
        }
        List<Integer> ordered=new ArrayList<>(seasons);
        java.util.Collections.sort(ordered);
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        for(Integer ss:ordered){
            Button b=btn(s("Сезон ","Season ")+ss,()->{episodeSeason=ss;episodeShown=0;showEpisodesList();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.rightMargin=px(8);row.addView(b,p);
        }
        if(ordered.size()>1){
            Button all=btn(s("Усі сезони","All seasons"),()->{episodeSeason=-1;episodeShown=0;showEpisodesList();});
            row.addView(all);
        }
        scroll.addView(row);l.addView(scroll);space(l,14);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);l.addView(list);
        int filtered=0,end=(episodeShown+80);
        for(int i=0;i<episodes.length();i++){
            JSONObject ep=episodes.optJSONObject(i);if(ep==null||(episodeSeason>=0&&ep.optInt("season")!=episodeSeason))continue;
            if(filtered>=episodeShown && filtered<end){
                final int index=i;
                String caption="S"+ep.optInt("season")+" E"+ep.optInt("number")+"  "+ep.optString("title");
                String voice=ep.optString("voice");if(!voice.isEmpty())caption+=" · "+voice;
                buttonRow(list,btn(caption,()->playEpisode(index)));
            }
            filtered++;
        }
        if(filtered>end)buttonRow(list,btn(s("Показати ще","Show more"),()->{episodeShown+=80;showEpisodesList();}));
        if(filtered==0)note(list,s("Немає серій для сезону","No episodes in this season"));
        focusFirst();
    }
    private void playEpisode(int index){
        JSONObject ep=episodes.optJSONObject(index);if(ep==null)return;
        currentEpisode=index;
        JSONObject source=new JSONObject();
        try{
            source.put("title",ep.optString("title"));
            source.put("url",ep.optString("url"));
        }catch(Exception ignored){}
        resolveAndPlay(source);
    }
    private void resolveAndPlay(JSONObject source){
        if(source==null)return;
        LinearLayout l=content(SOURCES);
        title(l,s("Підготовка відео...","Preparing video..."));
        note(l,source.optString("title"));
        busy(l);final int ticket=generation;
        async(()->NativeCore.resolve(source.optString("title"),source.optString("url")),response->{
            if(!response.optBoolean("ok")){
                note(l,response.optString("error",s("Потік недоступний","Stream unavailable")));
                buttonRow(l,btn(s("← Джерела","← Providers"),()->showSources(0)));
                return;
            }
            String url=response.optString("url");
            audioOptions=response.optJSONArray("audio");subtitleOptions=response.optJSONArray("subtitles");
            if(url.isEmpty()){note(l,s("Відеопосилання порожнє","Empty video URL"));return;}
            String referer=selectedSource!=null?selectedSource.optString("url"):source.optString("url");
            if(url.contains("movy.sx/") && (url.contains("/tv/")||url.contains("/movie/"))){
                final int second=generation;
                async(()->NativeCore.openMovy(url,0),movy->{
                    if(!movy.optBoolean("ok")){note(l,s("Не вдалося запустити потік Movy","Movy stream unavailable"));return;}
                    playingMovy=true;startPlayer(movy.optString("url"),referer,source.optString("title"));
                },second);
            }else{
                playingMovy=false;startPlayer(url,referer,source.optString("title"));
            }
        },ticket);
    }
    private void showIptv(int country,int offset){
        iptvCountry=country;iptvOffset=offset;playingIptv=false;
        LinearLayout l=content(IPTV);buttonRow(l,btn(s("← Головна","← Home"),()->browse("top")));
        title(l,"IPTV");
        note(l,s("Канали з відкритих списків UAPlay","Channels from UAPlay's open playlists"));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);l.addView(list);
        busy(list);final int ticket=generation;
        async(()->NativeCore.iptv(country,offset,80),response->{
            list.removeAllViews();
            if(response.optInt("status")!=0){
                note(list,s("Не вдалося завантажити IPTV-плейлист","Cannot load IPTV playlist"));return;
            }
            iptvTotal=response.optInt("count");
            note(list,s("Усього каналів: ","Total channels: ")+iptvTotal);
            JSONArray channels=response.optJSONArray("items");if(channels==null)return;
            for(int i=0;i<channels.length();i++){
                JSONObject channel=channels.optJSONObject(i);if(channel==null)continue;
                buttonRow(list,btn("▶ "+channel.optString("title"),()->{
                    playingIptv=true;currentEpisode=-1;
                    startPlayer(channel.optString("url"),channel.optString("referrer"),channel.optString("title"));
                }));
            }
            if(offset+channels.length()<iptvTotal){
                buttonRow(list,btn(s("Наступні канали →","More channels →"),()->showIptv(country,offset+80)));
            }
            focusFirst();
        },ticket);
    }
    private void showFavorites(){
        LinearLayout l=content(LIBRARY);buttonRow(l,btn(s("← Головна","← Home"),()->browse("top")));
        title(l,s("Моя бібліотека","My library"));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);l.addView(list);
        busy(list);final int ticket=generation;
        async(()->NativeCore.library(0),response->{
            list.removeAllViews();JSONArray items=response.optJSONArray("items");
            if(items==null||items.length()==0)note(list,s("Бібліотека порожня","Nothing saved"));
            else addMovieGrid(list,items);
            focusFirst();
        },ticket);
    }
    private void settings(){
        LinearLayout l=content(SETTINGS);
        buttonRow(l,btn(s("← Головна","← Home"),()->browse("top")));
        title(l,s("Налаштування","Settings"));
        note(l,s("Режим пристрою","Display mode"));
        buttonRow(l,btn(s("Телефон / планшет","Phone / tablet")+(!tv()?" ✓":""),()->setMode("phone")));
        buttonRow(l,btn(s("Телевізор","Television")+(tv()?" ✓":""),()->setMode("tv")));
        space(l,14);note(l,s("Мова","Language"));
        buttonRow(l,btn("Українська",()->setLanguage("uk")));
        buttonRow(l,btn("English",()->setLanguage("en")));
        space(l,14);note(l,"TMDB");
        buttonRow(l,btn(s("Ввести TMDB API-ключ","Set TMDB API key"),this::showKeyDialog));
        note(l,s("Ключ використовується для актуальних фільмів, серіалів та пошуку. Без нього доступний вбудований каталог.",
            "The key enables current movies, series, and search. Without it, bundled titles are shown."));
        note(l,s("UAPlay Android: нативні джерела, IPTV і плеєр Media3",
                 "UAPlay Android: native providers, IPTV and Media3 player"));
        focusFirst();
    }
    private void setLanguage(String language){
        prefs.edit().putString("lang",language).apply();NativeCore.language(language.equals("en"));settings();
    }
    private void showKeyDialog(){
        EditText edit=new EditText(this);edit.setSingleLine(true);edit.setHint("TMDB API key");
        edit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        FrameLayout frame=new FrameLayout(this);frame.setPadding(px(14),0,px(14),0);frame.addView(edit);
        new AlertDialog.Builder(this).setTitle("TMDB API key").setView(frame)
            .setNegativeButton(s("Скасувати","Cancel"),null)
            .setPositiveButton(s("Зберегти","Save"),(dialog,which)->{
                String value=edit.getText().toString().trim();
                if(value.matches("[A-Za-z0-9]{10,128}")){
                    NativeCore.setKey(value);
                    Toast.makeText(this,s("Ключ збережено","Key saved"),Toast.LENGTH_SHORT).show();
                    browse("top");
                }else Toast.makeText(this,s("Некоректний ключ","Invalid key"),Toast.LENGTH_LONG).show();
            }).show();
    }

    private void startPlayer(String url,String referer,String title){
        if(player!=null)releasePlayer();
        page=PLAYER;++generation;
        playingUrl=url;playingReferer=referer==null?"":referer;playingName=title;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true).setUserAgent("Mozilla/5.0 (Android TV; UAPlay)");
        Map<String,String> headers=new HashMap<>();
        if(!playingReferer.isEmpty())headers.put("Referer",playingReferer);
        http.setDefaultRequestProperties(headers);
        DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,http);
        player=new ExoPlayer.Builder(this)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(data)).build();
        playerView=new PlayerView(this);playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerShowTimeoutMs(6000);
        playerView.setControllerAutoShow(true);
        videoRoot=new FrameLayout(this);videoRoot.setBackgroundColor(Color.BLACK);
        videoRoot.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        videoBar=new LinearLayout(this);videoBar.setGravity(Gravity.CENTER);
        videoBar.setOrientation(LinearLayout.HORIZONTAL);
        videoBar.setPadding(px(8),px(6),px(8),px(6));
        videoBar.setBackgroundColor(Color.argb(190,11,14,20));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        videoRoot.addView(videoBar,p);
        VideoControls();
        setContentView(videoRoot);
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException err){
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(s("Помилка відтворення","Playback error"))
                    .setMessage(err.getErrorCodeName()+"\n"+(err.getMessage()==null?"":err.getMessage()))
                    .setPositiveButton(s("Повернутися","Back"),(dialog,which)->leavePlayer())
                    .setNegativeButton(s("Закрити","Dismiss"),null)
                    .show();
            }
        });
        Uri uri=Uri.parse(url);
        MediaItem.Builder item=new MediaItem.Builder().setUri(uri);
        if(url.toLowerCase(Locale.ROOT).contains(".m3u8"))item.setMimeType(MimeTypes.APPLICATION_M3U8);
        if(subtitleOptions!=null && subtitleOptions.length()>0) {
            List<MediaItem.SubtitleConfiguration> subs=new ArrayList<>();
            for(int i=0;i<subtitleOptions.length();i++){
                JSONObject sub=subtitleOptions.optJSONObject(i);
                if(sub==null)continue;
                String u=sub.optString("url");
                if(u.isEmpty()||!u.startsWith("http"))continue;
                String mime=u.toLowerCase(Locale.ROOT).contains(".vtt")?MimeTypes.TEXT_VTT:MimeTypes.APPLICATION_SUBRIP;
                subs.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(u))
                    .setMimeType(mime).setLanguage("uk").setLabel(sub.optString("label","UA")).build());
            }
            item.setSubtitleConfigurations(subs);
        }
        player.setMediaItem(item.build());player.prepare();player.play();
        if(!playingIptv && selectedMovie!=null){
            double sec=NativeCore.resume(idOf(selectedMovie),seriesOf(selectedMovie),titleOf(selectedMovie),originalOf(selectedMovie),yearOf(selectedMovie));
            if(sec>10)player.seekTo((long)(sec*1000));
        }
        if(tv()||hardwareTv())playerView.requestFocus();
        saveProgress=new Runnable(){
            @Override public void run(){
                if(page==PLAYER && player!=null){
                    storePosition();handler.postDelayed(this,10000);
                }
            }
        };
        handler.postDelayed(saveProgress,10000);
    }
    private void VideoControls(){
        if(videoBar==null)return;
        videoBar.removeAllViews();
        if(currentEpisode>=0){
            addMini(videoBar,btn("◀",()->nextEpisode(-1)));
            addMini(videoBar,btn("▶▶",()->nextEpisode(1)));
        }
        addMini(videoBar,btn("−10s",()->seekVideo(-10000)));
        addMini(videoBar,btn("+10s",()->seekVideo(10000)));
        if(audioOptions!=null&&audioOptions.length()>0)
            addMini(videoBar,btn(s("Озвучення","Dub"),this::selectDub));
        addMini(videoBar,btn(s("Вийти","Exit"),this::leavePlayer));
    }
    private void addMini(LinearLayout row,Button b){
        b.setTextSize(tv()?15:12);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.rightMargin=px(3);row.addView(b,p);
    }
    private void nextEpisode(int direction){
        if(episodes==null||currentEpisode<0)return;
        int next=currentEpisode+direction;
        if(next>=0&&next<episodes.length()){
            storePosition();releasePlayer();playEpisode(next);
        }
    }
    private void selectDub(){
        if(audioOptions==null||audioOptions.length()==0)return;
        String[] choices=new String[audioOptions.length()];
        for(int i=0;i<choices.length;i++){
            JSONObject v=audioOptions.optJSONObject(i);
            choices[i]=v==null?"":v.optString("label","Audio "+(i+1));
        }
        new AlertDialog.Builder(this).setTitle(s("Озвучення","Dub / audio"))
            .setItems(choices,(dialog,index)->{
                JSONObject v=audioOptions.optJSONObject(index);
                if(v==null)return;
                long resume=player!=null?player.getCurrentPosition():0;
                String u=v.optString("url");
                if(!u.isEmpty()){
                    startPlayer(u,playingReferer,playingName);
                    if(player!=null)player.seekTo(resume);
                }
            }).show();
    }
    private void seekVideo(long delta){
        if(player==null)return;
        long pos=Math.max(0,player.getCurrentPosition()+delta);
        if(player.getDuration()>0)pos=Math.min(pos,player.getDuration());
        player.seekTo(pos);playerView.showController();
    }
    private void storePosition(){
        if(player==null||playingIptv||selectedMovie==null)return;
        long ms=player.getCurrentPosition();if(ms<0)return;
        final int season,episode;
        if(currentEpisode>=0 && episodes!=null){
            JSONObject ep=episodes.optJSONObject(currentEpisode);
            season=ep==null?0:ep.optInt("season");
            episode=ep==null?0:ep.optInt("number");
        } else {season=0;episode=0;}
        NativeCore.progress(idOf(selectedMovie),seriesOf(selectedMovie),titleOf(selectedMovie),
            originalOf(selectedMovie),yearOf(selectedMovie),season,episode,ms/1000.0);
    }
    private void releasePlayer(){
        if(saveProgress!=null){handler.removeCallbacks(saveProgress);saveProgress=null;}
        if(player!=null){storePosition();player.release();player=null;}
        if(playerView!=null){playerView.setPlayer(null);playerView=null;}
        videoRoot=null;videoBar=null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(playingMovy){
            playingMovy=false;core.submit(NativeCore::closeMovy);
        }
    }
    private void leavePlayer(){
        boolean iptv=playingIptv;storePosition();releasePlayer();
        if(iptv){showIptv(iptvCountry,iptvOffset);}
        else if(selectedMovie!=null && seriesOf(selectedMovie) && episodes!=null && episodes.length()>0)showEpisodesList();
        else if(selectedMovie!=null)showSources(0);
        else browse("top");
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(page==PLAYER && player!=null && event.getAction()==KeyEvent.ACTION_DOWN){
            switch(event.getKeyCode()){
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_MEDIA_REWIND: seekVideo(-10000);return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: seekVideo(10000);return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if(player.isPlaying())player.pause();else player.play();
                    playerView.showController();return true;
                case KeyEvent.KEYCODE_BACK: leavePlayer();return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN: playerView.showController();return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){
        if(page==PLAYER)leavePlayer();
        else if(page==SOURCES&&selectedMovie!=null)details(selectedMovie);
        else if(page==EPISODES)showSources(0);
        else if(page==DETAIL||page==SETTINGS||page==IPTV||page==LIBRARY)browse("top");
        else super.onBackPressed();
    }
    @Override protected void onPause(){
        if(player!=null){storePosition();player.pause();}
        super.onPause();
    }
    @Override protected void onDestroy(){
        releasePlayer();core.shutdownNow();pictures.shutdownNow();super.onDestroy();
    }
}

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <sys/stat.h>
#include <pthread.h>
#include "catalog.h"
#include "library.h"
#include "iptv.h"
#include "movy_stream.h"
#include "platform.h"
#include "i18n.h"

extern void ua_curl_start(JavaVM *,JNIEnv *);
typedef struct {char *data;size_t used,capacity;} Json;
static pthread_mutex_t gate=PTHREAD_MUTEX_INITIALIZER;
static int ready=0;
static void grow(Json *j,size_t more) {
 size_t wanted=j->used+more+1;if(wanted<=j->capacity)return;
 size_t next=j->capacity?j->capacity:1024;
 while(next<wanted)next*=2;
 char *p=realloc(j->data,next);
 if(p){j->data=p;j->capacity=next;} 
}
static void raw(Json *j,const char *s){
 if(!s)s="";size_t n=strlen(s);grow(j,n);
 if(!j->data||j->used+n>=j->capacity)return;
 memcpy(j->data+j->used,s,n);j->used+=n;j->data[j->used]=0;
}
static void fmt(Json *j,const char *format,...) {
 char local[512];va_list a;va_start(a,format);
 int n=vsnprintf(local,sizeof(local),format,a);va_end(a);
 if(n<0)return;
 if(n<(int)sizeof(local)){raw(j,local);return;}
 char *tmp=malloc((size_t)n+1);if(!tmp)return;
 va_start(a,format);vsnprintf(tmp,(size_t)n+1,format,a);va_end(a);
 raw(j,tmp);free(tmp);
}
static void quote(Json *j,const char *s){
 raw(j,"\"");if(!s)s="";
 for(const unsigned char *p=(const unsigned char*)s;*p;p++){
   unsigned char c=*p;
   if(c=='\"')raw(j,"\\\"");
   else if(c=='\\')raw(j,"\\\\");
   else if(c=='\n')raw(j,"\\n");
   else if(c=='\r')raw(j,"\\r");
   else if(c=='\t')raw(j,"\\t");
   else if(c<32)fmt(j,"\\u%04x",c);
   else {char s2[2]={(char)c,0};raw(j,s2);}
 }
 raw(j,"\"");
}
static jstring finish(JNIEnv *env,Json *j){
 jstring result=(*env)->NewStringUTF(env,j->data?j->data:"{}");
 free(j->data);return result;
}
static char *utf(JNIEnv *env,jstring string){
 if(!string)return strdup("");
 const char *s=(*env)->GetStringUTFChars(env,string,NULL);
 if(!s)return strdup("");
 char *out=strdup(s);(*env)->ReleaseStringUTFChars(env,string,s);
 return out?out:strdup("");
}
static void movie_json(Json *j,const Movie *m){
 raw(j,"{\"id\":");fmt(j,"%d",m->id);
 raw(j,",\"series\":");raw(j,m->series?"true":"false");
 raw(j,",\"title\":");quote(j,m->title);
 raw(j,",\"original\":");quote(j,m->original);
 raw(j,",\"overview\":");quote(j,m->overview);
 raw(j,",\"poster\":");quote(j,m->poster);
 raw(j,",\"year\":");quote(j,m->year);
 fmt(j,",\"rating\":%.2f,\"runtime\":%d,\"seasons\":%d,\"episodes\":%d",
    m->rating,m->runtime_minutes,m->season_count,m->episode_count);
 raw(j,"}");
}
static Movie movie_in(JNIEnv *env,jint id,jboolean series,jstring title,jstring original,jstring year){
 Movie m={0};m.id=id;m.series=(int)series;
 char *s=utf(env,title);snprintf(m.title,sizeof(m.title),"%s",s);free(s);
 s=utf(env,original);snprintf(m.original,sizeof(m.original),"%s",s);free(s);
 s=utf(env,year);snprintf(m.year,sizeof(m.year),"%s",s);free(s);return m;
}
static void init(JNIEnv *env){
 if(ready)return;
 JavaVM *vm=NULL;(*env)->GetJavaVM(env,&vm);ua_curl_start(vm,env);
 mkdir(UAPLAY_DATA_ROOT,0700);
 mkdir(UAPLAY_DATA_DIR,0700);
 library_init();
 catalog_http_init();
 ready=1;
}
JNIEXPORT void JNICALL Java_com_small182_uaplay_android_NativeCore_init(JNIEnv *env,jclass cls){
 (void)cls;pthread_mutex_lock(&gate);init(env);pthread_mutex_unlock(&gate);
}
JNIEXPORT void JNICALL Java_com_small182_uaplay_android_NativeCore_language(JNIEnv *env,jclass cls,jboolean english){
 (void)env;(void)cls;ui_set_language(english?UI_ENGLISH:UI_UKRAINIAN);
}
JNIEXPORT jstring JNICALL Java_com_small182_uaplay_android_NativeCore_catalog(JNIEnv *env,jclass cls,jstring category,jstring query,jint page,jint sort){
 (void)cls;pthread_mutex_lock(&gate);init(env);
 Movie *items=calloc(CATALOG_MAX,sizeof(Movie));
 char error[256]={0};int more=0;
 char *cat=utf(env,category),*q=utf(env,query);
 int count=items?catalog_load_page_sorted(cat,q,(int)page,(int)sort,items,CATALOG_PAGE_SIZE,error,sizeof(error),&more):0;
 Json j={0};raw(&j,"{\"error\":");quote(&j,error);
 fmt(&j,",\"more\":%s,\"items\":[",more?"true":"false");
 for(int i=0;i<count;i++){if(i)raw(&j,",");movie_json(&j,&items[i]);}
 raw(&j,"]}");
 free(cat);free(q);free(items);
 jstring out=finish(env,&j);pthread_mutex_unlock(&gate);return out;
}
JNIEXPORT jstring JNICALL Java_com_small182_uaplay_android_NativeCore_details(JNIEnv *env,jclass cls,jint id,jboolean series,jstring title,jstring original,jstring year){
 (void)cls;pthread_mutex_lock(&gate);init(env);
 Movie m=movie_in(env,id,series,title,original,year);
 char genre[96]={0};int okay=catalog_fetch_details(&m,genre,sizeof(genre));
 Json j={0};raw(&j,"{\"ok\":");raw(&j,okay?"true":"false");
 raw(&j,",\"genre\":");quote(&j,genre);raw(&j,",\"movie\":");movie_json(&j,&m);raw(&j,"}");
 jstring out=finish(env,&j);pthread_mutex_unlock(&gate);return out;
}
JNIEXPORT jstring JNICALL Java_com_small182_uaplay_android_NativeCore_sources(JNIEnv *env,jclass cls,jint id,jboolean series,jstring title,jstring original,jstring year,jint provider){
 (void)cls;pthread_mutex_lock(&gate);init(env);
 Movie m=movie_in(env,id,series,title,original,year);
 Source *sources=calloc(UAKINO_MAX,sizeof(Source));
 char error[256]={0};int n=0;
 if(sources){
   n=uakino_search_provider(&m,sources,UAKINO_MAX,(int)provider,error,sizeof(error));
   if(n>1)n=catalog_rank_sources(&m,sources,n,(int)provider);
 }
 Json j={0};raw(&j,"{\"error\":");quote(&j,error);raw(&j,",\"items\":[");
 for(int i=0;i<n;i++){
   if(i)raw(&j,",");raw(&j,"{\"title\":");quote(&j,sources[i].title);
   raw(&j,",\"url\":");quote(&j,sources[i].url);raw(&j,"}");
 }
 raw(&j,"]}");free(sources);
 jstring out=finish(env,&j);pthread_mutex_unlock(&gate);return out;
}
JNIEXPORT jstring JNICALL Java_com_small182_uaplay_android_NativeCore_episodes(JNIEnv *env,jclass cls,jstring sourceName,jstring sourceUrl){
 (void)cls;pthread_mutex_lock(&gate);init(env);
 Source source={0};char *s=utf(env,sourceName);snprintf(source.title,sizeof(source.title),"%s",s);free(s);
 s=utf(env,sourceUrl);snprintf(source.url,sizeof(source.url),"%s",s);free(s);
 Episode *episodes=calloc(EPISODES_MAX,sizeof(Episode));
 char error[256]={0};int count=episodes?uakino_episodes(&source,episodes,EPISODES_MAX,error,sizeof(error)):0;
 Json j={0};raw(&j,"{\"error\":");quote(&j,error);raw(&j,",\"items\":[");
 for(int i=0;i<count;i++){
   if(i)raw(&j,",");
   fmt(&j,"{\"season\":%d,\"number\":%d,\"title\":",episodes[i].season,episodes[i].number);
   quote(&j,episodes[i].title);raw(&j,",\"voice\":");quote(&j,episodes[i].voice);
   raw(&j,",\"url\":");quote(&j,episodes[i].url);raw(&j,"}");
 }
 raw(&j,"]}");free(episodes);
 jstring out=finish(env,&j);pthread_mutex_unlock(&gate);return out;
}
JNIEXPORT jstring JNICALL Java_com_small182_uaplay_android_NativeCore_resolve(JNIEnv *env,jclass cls,jstring sourceName,jstring sourceUrl){
 (void)cls;pthread_mutex_lock(&gate);init(env);
 Source source={0};char *s=utf(env,sourceName);snprintf(source.title,sizeof(source.title),"%s",s);free(s);
 s=utf(env,sourceUrl);snprintf(source.url,sizeof(source.url),"%s",s);free(s);
 char stream[4096]={0},error[256]={0};
 int okay=uakino_resolve(&source,stream,sizeof(stream),error,sizeof(error));
 Json j={0};raw(&j,"{\"ok\":");raw(&j,okay?"true":"false");
 raw(&j,",\"error\":");quote(&j,error);
 raw(&j,",\"url\":");quote(&j,stream);raw(&j,",\"audio\":[");
 int n=uakino_resolved_audio_count();
 for(int i=0;i<n;i++){if(i)raw(&j,",");raw(&j,"{\"label\":");quote(&j,uakino_resolved_audio_label(i));
 raw(&j,",\"url\":");quote(&j,uakino_resolved_audio_url(i));raw(&j,"}");}
 raw(&j,"],\"subtitles\":[");
 n=uakino_resolved_subtitle_count();
 for(int i=0;i<n;i++){if(i)raw(&j,",");raw(&j,"{\"label\":");quote(&j,uakino_resolved_subtitle_label(i));
 raw(&j,",\"url\":");quote(&j,uakino_resolved_subtitle_url(i));raw(&j,"}");}
 raw(&j,"]}");
 jstring out=finish(env,&j);pthread_mutex_unlock(&gate);return out;
}
JNIEXPORT jstring JNICALL Java_com_small182_uaplay_android_NativeCore_openMovy(JNIEnv *env,jclass cls,jstring url,jint bandwidth){
 (void)cls;pthread_mutex_lock(&gate);init(env);
 char *up=utf(env,url),result[2048]={0};
 int okay=movy_stream_open(up,(long)bandwidth,result,sizeof(result));
 Json j={0};raw(&j,"{\"ok\":");raw(&j,okay?"true":"false");
 raw(&j,",\"url\":");quote(&j,okay?result:up);raw(&j,"}");
 free(up);jstring out=finish(env,&j);pthread_mutex_unlock(&gate);return out;
}
JNIEXPORT void JNICALL Java_com_small182_uaplay_android_NativeCore_closeMovy(JNIEnv *env,jclass cls){
 (void)env;(void)cls;pthread_mutex_lock(&gate);movy_stream_close();pthread_mutex_unlock(&gate);
}
JNIEXPORT void JNICALL Java_com_small182_uaplay_android_NativeCore_setKey(JNIEnv *env,jclass cls,jstring key){
 (void)cls;char *s=utf(env,key);
 char path[768];snprintf(path,sizeof(path),"%s/tmdb.key",UAPLAY_DATA_DIR);
 FILE *f=fopen(path,"wb");if(f){fprintf(f,"%s\n",s);fclose(f);}free(s);
}
JNIEXPORT jboolean JNICALL Java_com_small182_uaplay_android_NativeCore_hasKey(JNIEnv *env,jclass cls){
 (void)env;(void)cls;char path[768];snprintf(path,sizeof(path),"%s/tmdb.key",UAPLAY_DATA_DIR);
 FILE *f=fopen(path,"rb");if(!f)return JNI_FALSE;int ch=fgetc(f);fclose(f);return ch!=EOF;
}
JNIEXPORT jboolean JNICALL Java_com_small182_uaplay_android_NativeCore_isFavorite(JNIEnv *env,jclass cls,jint id,jboolean series,jstring title,jstring original,jstring year){
 (void)cls;pthread_mutex_lock(&gate);Movie m=movie_in(env,id,series,title,original,year);
 int result=library_favorite(&m);pthread_mutex_unlock(&gate);return result?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT void JNICALL Java_com_small182_uaplay_android_NativeCore_toggleFavorite(JNIEnv *env,jclass cls,jint id,jboolean series,jstring title,jstring original,jstring year){
 (void)cls;pthread_mutex_lock(&gate);Movie m=movie_in(env,id,series,title,original,year);
 library_toggle_favorite(&m);pthread_mutex_unlock(&gate);
}
JNIEXPORT jstring JNICALL Java_com_small182_uaplay_android_NativeCore_library(JNIEnv *env,jclass cls,jint mode){
 (void)cls;pthread_mutex_lock(&gate);Movie *movies=calloc(CATALOG_LOCAL_MAX,sizeof(Movie));
 int n=0;if(movies){
   if(mode==2)n=library_list_to_watch(movies,CATALOG_LOCAL_MAX);
   else n=library_list(movies,CATALOG_LOCAL_MAX,mode==0);
 }
 Json j={0};raw(&j,"{\"items\":[");
 for(int i=0;i<n;i++){if(i)raw(&j,",");movie_json(&j,&movies[i]);}
 raw(&j,"]}");free(movies);
 jstring out=finish(env,&j);pthread_mutex_unlock(&gate);return out;
}
JNIEXPORT void JNICALL Java_com_small182_uaplay_android_NativeCore_progress(JNIEnv *env,jclass cls,jint id,jboolean series,jstring title,jstring original,jstring year,jint season,jint episode,jdouble seconds){
 (void)cls;pthread_mutex_lock(&gate);Movie m=movie_in(env,id,series,title,original,year);
 if(episode>0)library_set_episode_progress(&m,season,episode,seconds);
 else library_set_progress(&m,seconds);
 pthread_mutex_unlock(&gate);
}
JNIEXPORT jdouble JNICALL Java_com_small182_uaplay_android_NativeCore_resume(JNIEnv *env,jclass cls,jint id,jboolean series,jstring title,jstring original,jstring year){
 (void)cls;pthread_mutex_lock(&gate);Movie m=movie_in(env,id,series,title,original,year);
 double secs=library_progress(&m);pthread_mutex_unlock(&gate);return secs;
}
JNIEXPORT jstring JNICALL Java_com_small182_uaplay_android_NativeCore_iptv(JNIEnv *env,jclass cls,jint country,jint start,jint size){
 (void)cls;pthread_mutex_lock(&gate);init(env);
 IptvCatalog cat={0};int fromcache=0;
 int status=iptv_load(country,&cat,&fromcache);
 Json j={0};fmt(&j,"{\"status\":%d,\"count\":%d,\"cache\":%s,\"items\":[",status,cat.count,fromcache?"true":"false");
 if(status==IPTV_OK){
   int offset=start<0?0:start,stop=offset+((size>0 && size<300)?size:100);
   for(int i=offset;i<stop && i<cat.count;i++){
      if(i>offset)raw(&j,",");
      const IptvChannel *ch=&cat.channels[i];
      raw(&j,"{\"title\":");quote(&j,ch->title);
      raw(&j,",\"url\":");quote(&j,ch->url);
      raw(&j,",\"logo\":");quote(&j,ch->logo);
      raw(&j,",\"referrer\":");quote(&j,ch->referrer);
      raw(&j,",\"agent\":");quote(&j,ch->agent);raw(&j,"}");
   }
 }
 raw(&j,"]}");iptv_free(&cat);
 jstring out=finish(env,&j);pthread_mutex_unlock(&gate);return out;
}

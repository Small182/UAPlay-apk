#include "curl/curl.h"
#include <jni.h>
#include <stdarg.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <ctype.h>
JavaVM *ua_jvm=NULL;
static jclass netcls=NULL;
static jmethodID fetchid=NULL;
static jfieldID bodyid=NULL,statusid=NULL,urlid=NULL;
typedef struct CURL {
 char *url,*referer,*useragent,*postfields,*effective;
 const struct curl_slist *headers;
 curl_write_callback writer;
 curl_xferinfo_callback progress;
 void *writedata,*progressdata;
 long timeout,connecttimeout,follow,failonerror;
 long status;
} CURL;
struct CURLU {char *url;};

static void setstr(char **dest,const char *src){free(*dest);*dest=src?strdup(src):NULL;}
void ua_curl_start(JavaVM *vm,JNIEnv *env) {
 ua_jvm=vm;
 jclass local=(*env)->FindClass(env,"com/small182/uaplay/android/NetworkBridge");
 if(!local)return;
 netcls=(jclass)(*env)->NewGlobalRef(env,local);
 (*env)->DeleteLocalRef(env,local);
 fetchid=(*env)->GetStaticMethodID(env,netcls,"fetch","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIZ)Lcom/small182/uaplay/android/NetworkBridge$Result;");
 jclass result=(*env)->FindClass(env,"com/small182/uaplay/android/NetworkBridge$Result");
 if(result){
   bodyid=(*env)->GetFieldID(env,result,"body","[B");
   statusid=(*env)->GetFieldID(env,result,"status","I");
   urlid=(*env)->GetFieldID(env,result,"effectiveUrl","Ljava/lang/String;");
   (*env)->DeleteLocalRef(env,result);
 }
}
CURL *curl_easy_init(void){CURL *c=calloc(1,sizeof(CURL));if(c){c->timeout=20;c->connecttimeout=10;c->follow=1;}return c;}
void curl_easy_cleanup(CURL *c){if(!c)return;free(c->url);free(c->referer);free(c->useragent);free(c->postfields);free(c->effective);free(c);}
void curl_easy_reset(CURL *c){if(!c)return;free(c->url);free(c->referer);free(c->useragent);free(c->postfields);free(c->effective);memset(c,0,sizeof(*c));c->timeout=20;c->connecttimeout=10;c->follow=1;}
CURLcode curl_easy_setopt(CURL *c,CURLoption option,...) {
 if(!c)return CURLE_FAILED_INIT;
 va_list ap;va_start(ap,option);
 switch(option){
 case CURLOPT_URL:setstr(&c->url,va_arg(ap,const char *));break;
 case CURLOPT_USERAGENT:setstr(&c->useragent,va_arg(ap,const char *));break;
 case CURLOPT_REFERER:setstr(&c->referer,va_arg(ap,const char *));break;
 case CURLOPT_POSTFIELDS:setstr(&c->postfields,va_arg(ap,const char *));break;
 case CURLOPT_HTTPHEADER:c->headers=va_arg(ap,const struct curl_slist *);break;
 case CURLOPT_WRITEFUNCTION:c->writer=va_arg(ap,curl_write_callback);break;
 case CURLOPT_WRITEDATA:c->writedata=va_arg(ap,void *);break;
 case CURLOPT_XFERINFOFUNCTION:c->progress=va_arg(ap,curl_xferinfo_callback);break;
 case CURLOPT_XFERINFODATA:c->progressdata=va_arg(ap,void *);break;
 case CURLOPT_CONNECTTIMEOUT:c->connecttimeout=va_arg(ap,long);break;
 case CURLOPT_TIMEOUT:c->timeout=va_arg(ap,long);break;
 case CURLOPT_FOLLOWLOCATION:c->follow=va_arg(ap,long);break;
 case CURLOPT_FAILONERROR:c->failonerror=va_arg(ap,long);break;
 default:(void)va_arg(ap,long);break;
 }
 va_end(ap);return CURLE_OK;
}
CURLcode curl_easy_perform(CURL *c) {
 if(!c || !c->url || !ua_jvm || !netcls || !fetchid)return CURLE_FAILED_INIT;
 JNIEnv *env=NULL;int attached=0;
 if((*ua_jvm)->GetEnv(ua_jvm,(void **)&env,JNI_VERSION_1_6)!=JNI_OK){
   if((*ua_jvm)->AttachCurrentThread(ua_jvm,&env,NULL)!=JNI_OK)return CURLE_FAILED_INIT;
   attached=1;
 }
 CURLcode result=CURLE_COULDNT_CONNECT;
 jstring jurl=(*env)->NewStringUTF(env,c->url);
 jstring jref=(*env)->NewStringUTF(env,c->referer?c->referer:"");
 jstring jua=(*env)->NewStringUTF(env,c->useragent?c->useragent:"UAPlay-Android/1.0");
 jstring jpost=(*env)->NewStringUTF(env,c->postfields?c->postfields:"");
 size_t headerlength=1;
 for(const struct curl_slist *h=c->headers;h;h=h->next)headerlength+=strlen(h->data)+1;
 char *headers=calloc(headerlength,1);
 if(headers){for(const struct curl_slist *h=c->headers;h;h=h->next){strcat(headers,h->data);strcat(headers,"\n");}}
 jstring jheaders=(*env)->NewStringUTF(env,headers?headers:"");free(headers);
 jobject response=(*env)->CallStaticObjectMethod(env,netcls,fetchid,jurl,jref,jua,jpost,jheaders,(jint)(c->connecttimeout>0?c->connecttimeout:10),(jint)(c->timeout>0?c->timeout:25),(jboolean)(c->follow!=0));
 if((*env)->ExceptionCheck(env)){(*env)->ExceptionClear(env);goto done;}
 if(!response)goto done;
 c->status=(long)(*env)->GetIntField(env,response,statusid);
 jobject newurl=(*env)->GetObjectField(env,response,urlid);
 if(newurl){const char *raw=(*env)->GetStringUTFChars(env,(jstring)newurl,NULL);if(raw){setstr(&c->effective,raw);(*env)->ReleaseStringUTFChars(env,(jstring)newurl,raw);}(*env)->DeleteLocalRef(env,newurl);}
 jbyteArray data=(jbyteArray)(*env)->GetObjectField(env,response,bodyid);
 if(!data){result=CURLE_GOT_NOTHING;(*env)->DeleteLocalRef(env,response);goto done;}
 jsize length=(*env)->GetArrayLength(env,data);
 char chunk[32768];jsize pos=0;result=CURLE_OK;
 while(pos<length){
   jsize count=length-pos<sizeof(chunk)?length-pos:(jsize)sizeof(chunk);
   (*env)->GetByteArrayRegion(env,data,pos,count,(jbyte *)chunk);
   if((*env)->ExceptionCheck(env)){(*env)->ExceptionClear(env);result=CURLE_RECV_ERROR;break;}
   if(c->writer && c->writer(chunk,1,(size_t)count,c->writedata)!=(size_t)count){result=CURLE_WRITE_ERROR;break;}
   pos+=count;
   if(c->progress && c->progress(c->progressdata,length,pos,0,0)!=0){result=CURLE_ABORTED_BY_CALLBACK;break;}
 }
 if(c->failonerror && c->status>=400)result=CURLE_HTTP_RETURNED_ERROR;
 (*env)->DeleteLocalRef(env,data);
 (*env)->DeleteLocalRef(env,response);
done:
 (*env)->DeleteLocalRef(env,jurl);(*env)->DeleteLocalRef(env,jref);(*env)->DeleteLocalRef(env,jua);(*env)->DeleteLocalRef(env,jpost);(*env)->DeleteLocalRef(env,jheaders);
 if(attached)(*ua_jvm)->DetachCurrentThread(ua_jvm);
 return result;
}
CURLcode curl_easy_getinfo(CURL *c,CURLINFO option,...) {
 if(!c)return CURLE_FAILED_INIT;
 va_list ap;va_start(ap,option);
 if(option==CURLINFO_RESPONSE_CODE){long *p=va_arg(ap,long *);if(p)*p=c->status;}
 if(option==CURLINFO_EFFECTIVE_URL){char **p=va_arg(ap,char **);if(p)*p=c->effective?c->effective:c->url;}
 va_end(ap);return CURLE_OK;
}
const char *curl_easy_strerror(CURLcode code) {
 switch(code){case CURLE_OK:return "OK";case CURLE_COULDNT_CONNECT:return "Connection failed";case CURLE_OPERATION_TIMEDOUT:return "Timeout";case CURLE_GOT_NOTHING:return "Empty response";case CURLE_WRITE_ERROR:return "Write failure";default:return "Network request failed";}
}
char *curl_easy_escape(CURL *c,const char *value,int length) {
 (void)c;if(!value)return NULL;if(length<=0)length=(int)strlen(value);
 char *out=malloc((size_t)length*3+1);if(!out)return NULL;char *p=out;
 for(int i=0;i<length;i++){unsigned char z=(unsigned char)value[i];if(isalnum(z)||z=='-'||z=='_'||z=='.'||z=='~')*p++=(char)z;else{sprintf(p,"%%%02X",z);p+=3;}}
 *p=0;return out;
}
void curl_free(void *p){free(p);}
struct curl_slist *curl_slist_append(struct curl_slist *list,const char *s){
 struct curl_slist *node=calloc(1,sizeof(*node));if(!node)return list;
 node->data=strdup(s?s:"");
 if(!list)return node;
 struct curl_slist *tail=list;while(tail->next)tail=tail->next;tail->next=node;return list;
}
void curl_slist_free_all(struct curl_slist *s){while(s){struct curl_slist *n=s->next;free(s->data);free(s);s=n;}}
CURLU *curl_url(void){return calloc(1,sizeof(CURLU));}
int curl_url_set(CURLU *u,int part,const char *value,unsigned flags){
 (void)flags;if(!u || part!=CURLUPART_URL||!value)return 1;
 if(strstr(value,"://")){setstr(&u->url,value);return 0;}
 if(!u->url)return 1;
 char *origin=strstr(u->url,"://");if(!origin)return 1;
 origin+=3;char *slash=strchr(origin,'/');size_t hostlen=slash?(size_t)(slash-u->url):strlen(u->url);
 size_t n=strlen(u->url)+strlen(value)+4;char *result=malloc(n);if(!result)return 1;
 if(value[0]=='/' && value[1]=='/'){snprintf(result,n,"https:%s",value);}
 else if(value[0]=='/'){snprintf(result,n,"%.*s%s",(int)hostlen,u->url,value);}
 else {const char *last=strrchr(u->url,'/');size_t prefix=last && last>origin?(size_t)(last-u->url+1):hostlen+1;snprintf(result,n,"%.*s%s",(int)prefix,u->url,value);}
 setstr(&u->url,result);free(result);return 0;
}
int curl_url_get(CURLU *u,int part,char **out,unsigned flags){(void)flags;if(!u||part!=CURLUPART_URL||!u->url||!out)return 1;*out=strdup(u->url);return 0;}
void curl_url_cleanup(CURLU *u){if(u){free(u->url);free(u);}}

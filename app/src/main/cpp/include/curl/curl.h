#ifndef UAPLAY_COMPAT_CURL_H
#define UAPLAY_COMPAT_CURL_H
#include <stddef.h>
#include <stdint.h>
#define LIBCURL_VERSION_NUM 0x075500
#define CURL_ERROR_SIZE 256
#define CURLPROTO_HTTPS 2L
#define CURLPROTO_HTTP 1L
#define CURL_HTTP_VERSION_1_1 2L
#define CURL_IPRESOLVE_V4 1L
#define CURLUPART_URL 1
#define CURLPAUSE_RECV 1
#define CURLPAUSE_CONT 0
typedef struct CURL CURL;
typedef struct CURLU CURLU;
typedef long long curl_off_t;
typedef size_t (*curl_write_callback)(char *,size_t,size_t,void *);
typedef int (*curl_xferinfo_callback)(void *,curl_off_t,curl_off_t,curl_off_t,curl_off_t);
typedef struct curl_slist {char *data;struct curl_slist *next;} curl_slist;
typedef enum {CURLE_OK=0,CURLE_UNSUPPORTED_PROTOCOL=1,CURLE_FAILED_INIT=2,
 CURLE_COULDNT_RESOLVE_HOST=6,CURLE_COULDNT_CONNECT=7,CURLE_HTTP_RETURNED_ERROR=22,
 CURLE_WRITE_ERROR=23,CURLE_OPERATION_TIMEDOUT=28,CURLE_SSL_CONNECT_ERROR=35,
 CURLE_GOT_NOTHING=52,CURLE_SEND_ERROR=55,CURLE_RECV_ERROR=56,
 CURLE_ABORTED_BY_CALLBACK=42} CURLcode;
typedef enum {
 CURLOPT_URL=1,CURLOPT_USERAGENT,CURLOPT_REFERER,CURLOPT_POSTFIELDS,
 CURLOPT_HTTPHEADER,CURLOPT_WRITEDATA,CURLOPT_WRITEFUNCTION,
 CURLOPT_CONNECTTIMEOUT,CURLOPT_TIMEOUT,CURLOPT_NOSIGNAL,
 CURLOPT_FOLLOWLOCATION,CURLOPT_MAXREDIRS,CURLOPT_HTTP_VERSION,
 CURLOPT_IPRESOLVE,CURLOPT_ACCEPT_ENCODING,CURLOPT_MAXCONNECTS,
 CURLOPT_FRESH_CONNECT,CURLOPT_FAILONERROR,CURLOPT_REDIR_PROTOCOLS,
 CURLOPT_REDIR_PROTOCOLS_STR,CURLOPT_LOW_SPEED_LIMIT,CURLOPT_LOW_SPEED_TIME,
 CURLOPT_TCP_KEEPALIVE,CURLOPT_NOPROGRESS,CURLOPT_XFERINFOFUNCTION,
 CURLOPT_XFERINFODATA
} CURLoption;
typedef enum { CURLINFO_RESPONSE_CODE=1, CURLINFO_EFFECTIVE_URL=2 } CURLINFO;
CURL *curl_easy_init(void);
void curl_easy_cleanup(CURL *c);
void curl_easy_reset(CURL *c);
CURLcode curl_easy_setopt(CURL *c,CURLoption option,...);
CURLcode curl_easy_perform(CURL *c);
CURLcode curl_easy_getinfo(CURL *c,CURLINFO option,...);
const char *curl_easy_strerror(CURLcode code);
char *curl_easy_escape(CURL *c,const char *url,int length);
void curl_free(void *p);
struct curl_slist *curl_slist_append(struct curl_slist *list,const char *string);
void curl_slist_free_all(struct curl_slist *list);
CURLU *curl_url(void);
int curl_url_set(CURLU *u,int part,const char *value,unsigned flags);
int curl_url_get(CURLU *u,int part,char **value,unsigned flags);
void curl_url_cleanup(CURLU *u);
#endif

package com.tencent.liteav.demo.roomutil.http;

import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.logging.HttpLoggingInterceptor;

import com.google.gson.JsonSyntaxException;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jac on 2017/10/30.
 */

public class HttpRequests {
    private static final String TAG = HttpRequests.class.getSimpleName();

    private final OkHttpClient okHttpClient;
    private static final MediaType MEDIA_JSON = MediaType.parse("application/json; charset=utf-8");
    private final String domain;

    private class HttpInteraptorLog implements HttpLoggingInterceptor.Logger{
        @Override
        public void log(String message) {
            Log.i("HttpRequest", message+"\n");
        }
    }

    public HttpRequests(String domain) {
        this.domain = domain;

        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(new HttpInteraptorLog());
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        this.okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
    }


    public void cancelAllRequests(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                okHttpClient.dispatcher().cancelAll();
            }
        }).start();
    }

    private <R extends HttpResponse> void request(Request request, final Class<R> rClass, final OnResponseCallback<R> callback){

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onResponse(-1, "网络请求超时，请检查网络", null);
            }

            @Override
            public void onResponse(Call call, okhttp3.Response response) throws IOException {
                String body = response.body().string();

                Gson gson = new Gson();

                try {
                    R resp = gson.fromJson(body, rClass);
                    String errorMessage = resp.message;
                    if (resp.code != 0) {
                        errorMessage += ("[err=" + resp.code + "]");
                    }
                    callback.onResponse(resp.code, errorMessage, resp);

                } catch (JsonSyntaxException e) {
                    onFailure(call, new IOException(e.getMessage()));
                }
            }
        });
    }

    public void getImLoginInfo(String userIdPrefix, final OnResponseCallback<HttpResponse.LoginInfo> callback){
        final Request request = new Request.Builder()
                .url(domain.concat("/get_im_login_info"))
                .post(RequestBody.create(MEDIA_JSON, "{\"userIDPrefix\":\""+userIdPrefix+"\"}"))
                .build();

        request(request, HttpResponse.LoginInfo.class, callback);

    }//getIMLoginInfo

    public void getRoomList(int index, int count,
                            final OnResponseCallback<HttpResponse.RoomList> callback){
        String body = "";

        try {
            body = new JsonBuilder()
                    .put("cnt", count)
                    .put("index", index)
                    .build();
        } catch (JSONException e) {
            callback.onResponse(-1, e.getMessage(), null);
            return;
        }

        final Request request = new Request.Builder()
                .url(domain.concat("/get_room_list"))
                .post(RequestBody.create(MEDIA_JSON, body))
                .build();

        request(request, HttpResponse.RoomList.class, callback);

    }//getRoomList

    public void getPushUrl(String userId, OnResponseCallback<HttpResponse.PushUrl> callback){

        String body = String.format("{\"userID\": \"%s\"}", userId);
        Request request = new Request.Builder().url(domain.concat("/get_push_url"))
                .post(RequestBody.create(MEDIA_JSON, body))
                .build();

        request(request, HttpResponse.PushUrl.class, callback);
    }

    public void getPushers(String roomId, final OnResponseCallback<HttpResponse.PusherList> callback){

        String body = String.format("{\"roomID\":\"%s\"}", roomId);

        Request request = new Request.Builder().url(domain.concat("/get_pushers"))
                .post(RequestBody.create(MEDIA_JSON, body))
                .build();

        request(request, HttpResponse.PusherList.class, callback);
    }


    public void createRoom (String userID, String roomName,
                           String userName,
                           String userAvatar,
                           String pushURL,
                           final OnResponseCallback<HttpResponse.CreateRoom> callback){

        String body = "";
        try {
            body = new JsonBuilder()
                    .put("userID", userID)
                    .put("roomName", roomName)
                    .put("userName", userName)
                    .put("pushURL", pushURL)
                    .put("userAvatar", userAvatar)
                    .build();
        } catch (JSONException e) {
            callback.onResponse(-1, e.getMessage(), null);
            return;
        }

        final Request request = new Request.Builder().url(domain.concat("/create_room"))
                .post(RequestBody.create(MEDIA_JSON, body))
                .build();

        request(request, HttpResponse.CreateRoom.class, callback);

    } //createRoom

    public void destroyRoom(String roomID, String userID, final OnResponseCallback<HttpResponse> callback){

        String body = "";
        try {
            body = new JsonBuilder()
                    .put("userID", userID)
                    .put("roomID", roomID)
                    .build();
        } catch (JSONException e) {
            callback.onResponse(-1, e.getMessage(), null);
            return;
        }

        Request request = new Request.Builder().url(domain.concat("/destroy_room"))
                .post(RequestBody.create(MEDIA_JSON, body))
                .build();

        request(request, HttpResponse.class, callback);

    } //leaveRoom


    public void addPusher(String roomID, String userID,
                          String  userName, String userAvatar,
                          String pushURL, final OnResponseCallback<HttpResponse> callback){
        String body = "";
        try {
            body = new JsonBuilder()
                    .put("userID", userID)
                    .put("roomID", roomID)
                    .put("userName", userName)
                    .put("userAvatar", userAvatar)
                    .put("pushURL", pushURL)
                    .build();
        } catch (JSONException e) {
            callback.onResponse(-1, e.getMessage(), null);
            return;
        }

        Request request = new Request.Builder().url(domain.concat("/add_pusher"))
                .post(RequestBody.create(MEDIA_JSON, body))
                .build();

        request(request, HttpResponse.class, callback);
    }

    public void delPusher(String roomID, String userID,
                          final OnResponseCallback<HttpResponse> callback){
        String body = "";
        try {
            body = new JsonBuilder()
                    .put("userID", userID)
                    .put("roomID", roomID)
                    .build();
        } catch (JSONException e) {
            callback.onResponse(-1, e.getMessage(), null);
            return;
        }

        Request request = new Request.Builder().url(domain.concat("/delete_pusher"))
                .post(RequestBody.create(MEDIA_JSON, body))
                .build();

        request(request, HttpResponse.class, callback);

    }

    public boolean heartBeat(String user_id, String room_id){
        String body = "";
        try {
            body = new JsonBuilder()
                    .put("userID", user_id)
                    .put("roomID", room_id)
                    .build();
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }

        Request request = new Request.Builder().url(domain.concat("/pusher_heartbeat"))
                .post(RequestBody.create(MEDIA_JSON, body))
                .build();

        try {
            okhttp3.Response response = okHttpClient.newCall(request).execute();
            Gson gson = new Gson();
            try {
                HttpResponse resp = gson.fromJson(response.body().string(), HttpResponse.class);
                if (resp.code == 0)
                    return true;
            } catch (JsonSyntaxException e) {
                e.printStackTrace();
                return false;
            }

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }


    private class JsonBuilder{
        private JSONObject obj;
        public JsonBuilder(){
            obj = new JSONObject();
        }

        public JsonBuilder put(String k, int v) throws JSONException{
            obj.put(k, v);
            return this;
        }

        public JsonBuilder put(String k, long v) throws JSONException{
            obj.put(k, v);
            return this;
        }

        public JsonBuilder put(String k, double v) throws JSONException{
            obj.put(k, v);
            return this;
        }

        public JsonBuilder put(String k, String v) throws JSONException{
            obj.put(k, v);
            return this;
        }

        public String build(){
            return obj.toString();
        }

    }

    public interface OnResponseCallback<T>{
        public void onResponse(final int retcode, final @Nullable String retmsg, final @Nullable T data);
    }


}

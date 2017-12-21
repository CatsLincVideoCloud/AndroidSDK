package com.tencent.liteav.demo.rtcroom.ui.double_room;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.rtcroom.IRTCRoomListener;
import com.tencent.liteav.demo.roomutil.commondef.PusherInfo;
import com.tencent.liteav.demo.rtcroom.RTCRoom;
import com.tencent.liteav.demo.roomutil.commondef.SelfAccountInfo;
import com.tencent.liteav.demo.roomutil.misc.CommonAppCompatActivity;
import com.tencent.liteav.demo.roomutil.misc.NameGenerator;
import com.tencent.liteav.demo.rtcroom.ui.double_room.fragment.RTCDoubleRoomChatFragment;
import com.tencent.liteav.demo.rtcroom.ui.double_room.fragment.RTCDoubleRoomListFragment;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.logging.HttpLoggingInterceptor;

public class RTCDoubleRoomActivity extends CommonAppCompatActivity implements RTCDoubleRoomActivityInterface {

    private static final String TAG = RTCDoubleRoomActivity.class.getSimpleName();

    private final static String DOMAIN = "https://lvb.qcloud.com/weapp/double_room";   //测试环境 https://drourwkp.qcloud.la

    public final Handler uiHandler = new Handler();

    private RTCRoom RTCRoom;
    private String userId;
    private String userName;
    private String avatarUrl = "avatar";
    private TextView titleTextView;
    private TextView globalLogTextview;
    private ScrollView globalLogTextviewContainer;
    private Runnable mRetryInitRoomRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rtc_double_room);

        findViewById(R.id.rtc_multi_room_back_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        titleTextView = ((TextView) findViewById(R.id.rtc_mutil_room_title_textview));

        titleTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mRetryInitRoomRunnable != null) {
                    synchronized (RTCDoubleRoomActivity.this) {
                        mRetryInitRoomRunnable.run();
                        mRetryInitRoomRunnable = null;
                    }
                }
            }
        });

        globalLogTextview = ((TextView) findViewById(R.id.rtc_multi_room_global_log_textview));
        globalLogTextview.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {

                 new AlertDialog.Builder(RTCDoubleRoomActivity.this, R.style.RtmpRoomDialogTheme)
                        .setTitle("Global Log")
                        .setMessage("清除Log")
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).setPositiveButton("清除", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                globalLogTextview.setText("");
                                dialog.dismiss();
                            }
                        }).show();

                return true;
            }
        });

        globalLogTextviewContainer = ((ScrollView) findViewById(R.id.rtc_mutil_room_global_log_container));

        RTCRoom = new RTCRoom(this);
        RTCRoom.setRTCRoomListener(new MemberEventListener());
        init();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    private void init(){
        initRoom();
    }

    public void showGlobalLog(final boolean enable) {
        if (uiHandler != null)
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    globalLogTextviewContainer.setVisibility(enable ? View.VISIBLE : View.GONE);
                }
            });
    }

    private SimpleDateFormat dataFormat = new SimpleDateFormat("HH:mm:ss");

    public void printGlobalLog(String format, Object ...args){
        String line = String.format("[%s] %s\n", dataFormat.format(new Date()), String.format(format, args));
        Log.i("0x256", line);

        globalLogTextview.append(line);
        if (globalLogTextviewContainer.getVisibility() != View.GONE){
            globalLogTextviewContainer.post(new Runnable() {
                @Override
                public void run() {
                    globalLogTextviewContainer.fullScroll(ScrollView.FOCUS_DOWN);
                }
            });
        }
    }

    @Override
    public void onPermissionDisable() {
        new AlertDialog.Builder(this, R.style.RtmpRoomDialogTheme)
                .setMessage("需要录音和摄像头权限，请到【设置】【应用】打开")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
    }

    @Override
    public void onPermissionGranted() {

    }

    private Runnable closeTipRunnable = new Runnable() {
        @Override
        public void run() {
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    setTitle("双人聊天");
                }
            });
        }
    };


    private class LoginInfoResponse {
        public int code;
        public String message;
        public String userID;
        public int    sdkAppID;
        public String accType;
        public String userSig;
    }

    private class HttpInterceptorLog implements HttpLoggingInterceptor.Logger{
        @Override
        public void log(String message) {
            Log.i("HttpRequest", message+"\n");
        }
    }

    private void initRoom(){
        setTitle("连接...");

        userName = NameGenerator.getRandomName(); //分配随机名字

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new HttpLoggingInterceptor(new HttpInterceptorLog()).setLevel(HttpLoggingInterceptor.Level.BODY))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        final MediaType MEDIA_JSON = MediaType.parse("application/json; charset=utf-8");

        final Request request = new Request.Builder()
                .url(DOMAIN.concat("/get_im_login_info"))
                .post(RequestBody.create(MEDIA_JSON, "{\"userIDPrefix\":\"android\"}"))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setTitle("获取登录信息失败，点击重试");
                        //失败后点击Title可以重试

                            setRetryRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(RTCDoubleRoomActivity.this, "重试...", Toast.LENGTH_SHORT).show();
                                    initRoom();
                                }
                            });

                        printGlobalLog(String.format("[Activity]获取登录信息失败{%s}", e.getMessage()));
                    }
                });
            }

            @Override
            public void onResponse(final Call call, okhttp3.Response response) throws IOException {
                String body = response.body().string();
                Gson gson = new Gson();

                try {
                    LoginInfoResponse resp = gson.fromJson(body, LoginInfoResponse.class);

                    if (resp.code != 0){
                        setTitle("获取登录信息失败");
                        printGlobalLog(String.format("[Activity]获取登录信息失败：{%s}", resp.message));
                    }else {

                        final SelfAccountInfo selfAccountInfo = new SelfAccountInfo(
                                resp.userID,
                                userName,
                                avatarUrl,
                                resp.userSig,
                                resp.accType,
                                resp.sdkAppID);

                        doInit(selfAccountInfo);

                    }
                } catch (JsonSyntaxException e) {
                    e.printStackTrace();
                }
            }
        });


    }


    private void doInit(final SelfAccountInfo selfAccountInfo){

        RTCRoom.init(DOMAIN,
                selfAccountInfo,
                new RTCRoom.InitCallback() {
                    @Override
                    public void onError(int errCode, String errInfo) {
                        setTitle(errInfo);

                        setRetryRunnable(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(RTCDoubleRoomActivity.this, "重试...", Toast.LENGTH_SHORT).show();
                                doInit(selfAccountInfo);

                            }
                        });

                        printGlobalLog(String.format("[Activity]初始化失败：{%s}", errInfo));
                    }

                    @Override
                    public void onSuccess(String userId) {
                        setTitle("IM初始化成功");
                        uiHandler.postDelayed(closeTipRunnable, 1000);

                        RTCDoubleRoomActivity.this.userId = userId;
                        printGlobalLog("[Activity]初始化成功,userID{%s}", userId);

                        Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
                        if (!(fragment instanceof RTCDoubleRoomChatFragment)) {
                            FragmentTransaction ft = getFragmentManager().beginTransaction();
                            fragment = RTCDoubleRoomListFragment.newInstance(userId);
                            ft.replace(R.id.rtmproom_fragment_container, fragment);
                            ft.commit();
                        }
                    }
                });

    }


    @Override
    public void onBackPressed() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
        if (fragment instanceof RTCDoubleRoomChatFragment){
            ((RTCDoubleRoomChatFragment) fragment).onBackPressed();
        }else {
            super.onBackPressed();
        }
    }

    @Override
    public RTCRoom getRTCRoom() {
        return RTCRoom;
    }

    @Override
    public String getSelfUserID() {
        return userId;
    }

    @Override
    public String getSelfUserName() {
        return userName;
    }

    private void doSetTitle(String s){
        if (s == null) s = " ";
        int id ;
        String ss = NameGenerator.replaceNonPrintChar(s, 20, "...", false);
        if (ss != null && ss.length() > 10)
            id = R.dimen.title_size_small;
        else if (ss != null && ss.length() > 4){
            id = R.dimen.title_size_mid;
        }else {
            id = R.dimen.title_size_big;
        }
        if (mRetryInitRoomRunnable != null){
            synchronized (RTCDoubleRoomActivity.this){
                mRetryInitRoomRunnable = null;
            }
        }
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(id));
        titleTextView.setText(ss);
    }

    @Override
    public void setTitle(final String s) {

        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                titleTextView.setLinksClickable(false);
                doSetTitle(s);
            }
        });
    }

    public void setTitleAsAnchor(final String s){

        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                titleTextView.setLinksClickable(true);
                doSetTitle(s);
            }
        });
    }

    public void setRetryRunnable(final Runnable runnable){
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (RTCDoubleRoomActivity.this) {
                    mRetryInitRoomRunnable = runnable;
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RTCRoom.setRTCRoomListener(null);
        RTCRoom.unInit();
    }

    private final class MemberEventListener implements IRTCRoomListener {

        @Override
        public void onPusherJoin(PusherInfo pusher) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof RTCDoubleRoomChatFragment && fragment.isVisible()){
                ((RTCDoubleRoomChatFragment) fragment).onPusherJoin(pusher);
            }
        }

        @Override
        public void onPusherQuit(PusherInfo member) {

            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof RTCDoubleRoomChatFragment && fragment.isVisible()){
                ((RTCDoubleRoomChatFragment) fragment).onPusherQuit(member);
            }
        }

        @Override
        public void onRoomClosed(String roomId) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof RTCDoubleRoomChatFragment && fragment.isVisible()){
                ((RTCDoubleRoomChatFragment) fragment).onRoomClosed(roomId);
            }
        }

        @Override
        public void onDebugLog(String line) {
            printGlobalLog(line);
        }

        @Override
        public void onGetPusherList(List<PusherInfo> pusherInfoList) {
            for (PusherInfo pusherInfo : pusherInfoList) {
                onPusherJoin(pusherInfo);
            }
        }

        @Override
        public void onRecvRoomTextMsg(String roomid, String userid, String userName, String userAvatar, String msg) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof RTCDoubleRoomChatFragment && fragment.isVisible()){
                ((RTCDoubleRoomChatFragment) fragment).onRecvRoomTextMsg(roomid, userid, userName, userAvatar, msg);
            }
        }

        @Override
        public void onError(final int errorCode, final String errorMessage) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.rtmproom_fragment_container);
            if (fragment instanceof RTCDoubleRoomChatFragment && fragment.isVisible()){
                ((RTCDoubleRoomChatFragment) fragment).onError(errorCode, errorMessage);
            }
        }
    }

}

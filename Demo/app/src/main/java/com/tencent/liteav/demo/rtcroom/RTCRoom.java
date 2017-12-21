package com.tencent.liteav.demo.rtcroom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;

import com.tencent.liteav.demo.roomutil.im.IMMessageMgr;
import com.tencent.liteav.demo.roomutil.commondef.PusherInfo;
import com.tencent.liteav.demo.roomutil.commondef.RoomInfo;
import com.tencent.liteav.demo.roomutil.commondef.SelfAccountInfo;
import com.tencent.liteav.demo.roomutil.http.HttpRequests;
import com.tencent.liteav.demo.roomutil.http.HttpResponse;
import com.tencent.rtmp.ITXLivePlayListener;
import com.tencent.rtmp.ITXLivePushListener;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.TXLivePlayer;
import com.tencent.rtmp.TXLivePushConfig;
import com.tencent.rtmp.TXLivePusher;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by jac on 2017/11/4.
 * Copyright © 2013-2017 Tencent Cloud. All Rights Reserved.
 */
public class RTCRoom implements IMMessageMgr.IMMessageListener {
    private static final String TAG = RTCRoom.class.getSimpleName();

    public static int RTCROOM_VIDEO_RATIO_9_16    = 1; //视频分辨率为9:16
    public static int RTCROOM_VIDEO_RATIO_3_4     = 2; //视频分辨率为3:4
    public static int RTCROOM_VIDEO_RATIO_1_1     = 3; //视频分辨率为1:1

    private Context                     mContext;
    private Handler                     mHandler;

    private TXLivePusher                mTXLivePusher;
    private TXLivePushListenerImpl      mTXLivePushListener;

    private HashMap<String, PlayerItem> mPlayers     = new LinkedHashMap<>();
    private RoomManager                 mRoomManager = new RoomManager();

    private IMMessageMgr                mIMMessageMgr;
    private HttpRequests                mHttpRequest;
    private HeartBeatThread             mHeartBeatThread;

    private RoomListenerCallback        roomListenerCallback;

    /**
     * RealTime ChatRoom 实时音视频通话房间
     */

    public RTCRoom(Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        roomListenerCallback = new RoomListenerCallback(null);

        mIMMessageMgr = new IMMessageMgr(context);
        mIMMessageMgr.setIMMessageListener(this);

        mHeartBeatThread = new HeartBeatThread();
    }

    /**
     * 设置房间事件回调
     * @param listener
     */
    public void setRTCRoomListener(IRTCRoomListener listener) {
        roomListenerCallback.setRoomMemberEventListener(listener);
    }

    /**
     * RTCRoom 发送文本消息Callback
     */
    public interface SendTextMessageCallback{
        void onError(int errCode, String errInfo);
        void onSuccess();
    }

    /**
     * 发送文本消息
     * @param message
     * @param callback
     */
    public void sendRoomTextMsg(@NonNull String message, final SendTextMessageCallback callback){
        mIMMessageMgr.sendGroupMessage(mRoomManager.getSelfUserName(), mRoomManager.getAvatarUrl(), message, new IMMessageMgr.Callback() {
            @Override
            public void onError(final int code, final String errInfo) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onError(code, errInfo);
                        }
                    }
                });
            }

            @Override
            public void onSuccess(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    }
                });
            }
        });
    }

    /**
     * RTCRoom 初始化Callback
     */
    public interface InitCallback  {
        void onError(int errCode, String errInfo);
        void onSuccess(String userId);
    }

    /**
     * 初始化RTCRoom 上下文
     * @param serverDomain      服务器域名地址
     * @param selfAccountInfo   用户初始化信息
     * @param callback          初始化完成的回调
     */
    public void init(@NonNull String serverDomain,
                     final SelfAccountInfo selfAccountInfo,
                     final InitCallback callback) {

        final String selfUserID   = selfAccountInfo.userID;
        final String selfUserName = selfAccountInfo.userName;
        final String selfUserSig  = selfAccountInfo.userSig;
        final String selfHeadPic  = selfAccountInfo.userAvatar;
        final int selfAppID       = selfAccountInfo.sdkAppID;

        if (mHttpRequest != null) {
            mHttpRequest.cancelAllRequests();
            mHttpRequest = null;
        }
        mHttpRequest = new HttpRequests(serverDomain);
        
        mRoomManager.setState(RoomState.Absent);
        mRoomManager.setSelfUserID(selfUserID);
        mRoomManager.setSelfUserName(selfUserName);
        mRoomManager.setAvatarUrl(selfHeadPic);

        final MainCallback cb = new MainCallback<InitCallback, String>(callback);

        // 初始化IM SDK，内部完成login
        mIMMessageMgr.initialize(selfUserID, selfUserSig, selfAppID, new IMMessageMgr.Callback() {
            @Override
            public void onError(int code, String errInfo) {
                roomListenerCallback.printLog("[RTCRoom] 初始化失败: %s(%d)", errInfo, code);
                cb.onError(code, errInfo);
            }

            @Override
            public void onSuccess(Object... args) {
                roomListenerCallback.printLog("[RTCRoom] 初始化成功, userID {%s}, " + "sdkAppID {%s}", selfUserID, selfAppID);
                cb.onSuccess(selfUserID);
            }
        });
    }

    /**
     * 反初始化
     */
    public void unInit() {
        roomListenerCallback.onDebugLog("[RTCRoom] unInit");
        mIMMessageMgr.setIMMessageListener(null);
        mIMMessageMgr.unInitialize();
        mHeartBeatThread.stopHeartbeat();
        mHeartBeatThread.quit();
    }

    /**
     * RTCRoom 获取房间列表Callback
     */
    public interface GetRoomListCallback{
        void onError(int errCode, String errInfo);
        void onSuccess(ArrayList<RoomInfo> roomInfoList);
    }

    /**
     * 获取房间列表，分页获取
     * @param index     获取的房间开始索引，从0开始计算
     * @param count     获取的房间个数
     * @param callback  拉取房间列表完成的回调，回调里返回获取的房间列表信息，如果个数小于cnt则表示已经拉取所有的房间列表
     */
    public void getRoomList(int index, int count,final GetRoomListCallback callback){
        mHttpRequest.getRoomList(index, count, new HttpRequests.OnResponseCallback<HttpResponse.RoomList>() {
            @Override
            public void onResponse(final int retcode, final @Nullable String retmsg, @Nullable HttpResponse.RoomList data) {
                if (retcode != HttpResponse.CODE_OK || data == null || data.rooms == null){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(retcode, retmsg);
                        }
                    });
                }else {
                    final ArrayList<RoomInfo> arrayList = new ArrayList<>(data.rooms.size());
                    arrayList.addAll(data.rooms);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(arrayList);
                        }
                    });
                }
            }
        });
    }

    /**
     * RTCRoom 创建房间Callback
     */
    public interface CreateRoomCallback {
        void onError(int errCode, String errInfo);
        void onSuccess(String name);
    }

    /**
     * 创建房间，由会议创建者调用
     * @param roomName  房间名
     * @param cb        房间创建完成的回调，里面会携带roomID
     */
    public void createRoom(final String roomName,
                           final CreateRoomCallback cb) {

        mRoomManager.setRoomName(roomName);

        //1. 在应用层调用startLocalPreview，启动本地预览

        final MainCallback callback = new MainCallback<CreateRoomCallback, String>(cb);

        //2. 请求CGI:get_push_url，异步获取到推流地址pushUrl
        mHttpRequest.getPushUrl(mRoomManager.getSelfUserID(), new HttpRequests.OnResponseCallback<HttpResponse.PushUrl>() {
            @Override
            public void onResponse(int retcode, @Nullable String retmsg, @Nullable HttpResponse.PushUrl data) {
                if (retcode == HttpResponse.CODE_OK && data != null && data.pushURL != null) {
                    final String pushURL = data.pushURL;

                    //3.开始推流
                    startPushStream(pushURL, new PusherStreamCallback() {
                        @Override
                        public void onError(int errCode, String errInfo) {
                            callback.onError(errCode, errInfo);
                        }

                        @Override
                        public void onSuccess() {
                            //推流过程中，可能会重复收到PUSH_EVT_PUSH_BEGIN事件，onSuccess可能会被回调多次，如果已经创建的房间，直接返回
                            String roomID = mRoomManager.getRoomId();
                            if (roomID != null && roomID.length() > 0) {
                                return;
                            }
                            //4.推流成功，请求CGI:create_room
                            doCreateRoom(pushURL, new CreateRoomCallback() {
                                @Override
                                public void onError(int errCode, String errInfo) {
                                    callback.onError(errCode, errInfo);
                                }

                                @Override
                                public void onSuccess(final String roomId) {
                                    mRoomManager.setRoomId(roomId);
                                    mRoomManager.setState(RoomState.Created);

                                    //5.调用IM的joinGroup，参数是roomId（roomId就是groupId）
                                    jionGroup(roomId, new JoinGroupCallback() {
                                        @Override
                                        public void onError(int errCode, String errInfo) {
                                            callback.onError(errCode, errInfo);
                                        }

                                        @Override
                                        public void onSuccess() {
                                            mHeartBeatThread.startHeartbeart(); //启动心跳
                                            mRoomManager.setState(RoomState.Jioned);
                                            callback.onSuccess(roomId);
                                        }
                                    });
                                }
                            });
                        }
                    });

                }
                else {
                    callback.onError(retcode, "获取推流地址失败");
                }
            }
        });

    }

    /**
     * RTCRoom 进入房间Callback
     */
    public interface EnterRoomCallback{
        void onError(int errCode, String errInfo);
        void onSuccess();
    }

    /**
     * 进入房间，由会议参与者调用
     * @param roomID    房间号
     * @param cb        进入房间完成的回调
     */
    public void enterRoom(@NonNull final String roomID,
                          final EnterRoomCallback cb) {

        mRoomManager.setRoomId(roomID);

        //1. 应用层调用startLocalPreview，启动本地预览

        final MainCallback<EnterRoomCallback, Object> callback = new MainCallback<EnterRoomCallback, Object>(cb);

        //2. 调用IM的joinGroup
        jionGroup(roomID, new JoinGroupCallback() {
            @Override
            public void onError(int code, String errInfo) {
                callback.onError(code, errInfo);
            }

            @Override
            public void onSuccess() {

                //3. 请求CGI:get_pushers，异步获取房间里所有正在推流的成员
                mHttpRequest.getPushers(roomID, new HttpRequests.OnResponseCallback<HttpResponse.PusherList>() {
                    @Override
                    public void onResponse(int retcode, @Nullable String retmsg,  @Nullable HttpResponse.PusherList data) {
                        if (retcode == HttpResponse.CODE_OK && data!= null && data.pushers != null){
                            //4. 调用listener.onGetPusherList，把房间成员通知出去（应用层代码在收到这个通知后，调用addRemoteView播放每一个成员的流）
                            List<PusherInfo> pusherList = data.pushers;
                            if (pusherList != null && pusherList.size() > 0) {
                                for (PusherInfo pusher: pusherList) {
                                    if (pusher.userID != null && pusher.userID.equalsIgnoreCase(mRoomManager.getSelfUserID())) {
                                        pusherList.remove(pusher);
                                    }
                                }
                                roomListenerCallback.onGetPusherList(pusherList);
                            }

                            mRoomManager.mergerPushers(pusherList, null, null);
                        }
                        else {
                            callback.onError(retcode, retmsg);
                        }
                    }
                });

                //5. 请求CGI:get_push_url，异步获取到推流地址pushUrl
                mHttpRequest.getPushUrl(mRoomManager.getSelfUserID(), new HttpRequests.OnResponseCallback<HttpResponse.PushUrl>() {
                    @Override
                    public void onResponse(int retcode, @Nullable String retmsg, final @Nullable HttpResponse.PushUrl data) {
                        if (retcode == HttpResponse.CODE_OK && data != null && data.pushURL != null) {
                            //6. 开始推流
                            startPushStream(data.pushURL, new PusherStreamCallback() {
                                @Override
                                public void onError(int code, String info) {
                                    callback.onError(code, info);
                                }

                                @Override
                                public void onSuccess() {
                                    //7. 推流成功，请求CGI:add_pusher，把自己加入房间成员列表
                                    addPusher(roomID, data.pushURL, new AddPusherCallback() {
                                        @Override
                                        public void onError(int code, String info) {
                                            callback.onError(code, info);
                                        }

                                        @Override
                                        public void onSuccess() {
                                            mHeartBeatThread.startHeartbeart();// 开启心跳
                                            mRoomManager.setState(mRoomManager.getState() | RoomState.Jioned);
                                            callback.onSuccess();
                                        }
                                    });
                                }
                            });
                        } else {
                            callback.onError(retcode, "获取推流地址失败");
                        }
                    }
                });
            }
        });
    }

    /**
     * RTCRoom 离开房间Callback
     */
    public interface ExitRoomCallback{
        void onError(int errCode, String errInfo);
        void onSuccess();
    }

    /**
     * 离开房间
     * @param callback 离开房间完成的回调
     */
    public void exitRoom(final ExitRoomCallback callback) {

        final MainCallback cb = new MainCallback<ExitRoomCallback, Object>(callback);

        //1. 应用层结束播放所有的流

        //2. 结束心跳
        mHeartBeatThread.stopHeartbeat();

        //3. 结束本地推流
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopLocalPreview();
            }
        });

        //4. 关闭所有播放器，清理房间信息
        for (Map.Entry<String, PlayerItem> entry : mPlayers.entrySet()) {
            entry.getValue().destroy();
        }
        mPlayers.clear();

        //5. 调用IM的quitGroup
        mIMMessageMgr.quitGroup(mRoomManager.getRoomId(), new IMMessageMgr.Callback() {
            @Override
            public void onError(int code, String errInfo) {
                //cb.onError(code, errInfo);
            }

            @Override
            public void onSuccess(Object... args) {
                //cb.onSuccess();
            }
        });

        //6. 退出房间：请求CGI:delete_pusher，把自己从房间成员列表里删除（后台会判断如果是房间创建者退出房间，则会直接解散房间）
        mHttpRequest.delPusher(mRoomManager.getRoomId(), mRoomManager.getSelfUserID(), new HttpRequests.OnResponseCallback<HttpResponse>() {
            @Override
            public void onResponse(int retcode, @Nullable String retmsg, @Nullable HttpResponse data) {
                if (retcode == HttpResponse.CODE_OK || retcode == 5) {
                    mRoomManager.setState(RoomState.Empty);
                    roomListenerCallback.printLog("[RTCRoom] UserID{%s} 退出房间 {%s}  成功", mRoomManager.getSelfUserID(), mRoomManager.getRoomId());
                    cb.onSuccess();
                }
                else {
                    roomListenerCallback.printLog("[RTCRoom] UserID{%s} 退出房间 {%s}  失败", mRoomManager.getSelfUserID(), mRoomManager.getRoomId());
                    cb.onError(retcode, retmsg);
                }
            }
        });

        //清理roomManager
        mRoomManager.clean();
    }

    /**
     * 启动摄像头预览
     * @param videoView 摄像头预览组件
     */
    public synchronized void startLocalPreview(final @NonNull TXCloudVideoView videoView) {
        roomListenerCallback.onDebugLog("[RTCRoom] startLocalPreview");

        initLivePusher();

        if (mTXLivePusher != null) {
            videoView.setVisibility(View.VISIBLE);
            mTXLivePusher.startCameraPreview(videoView);
        }
    }

    /**
     * 停止摄像头预览
     */
    public synchronized void stopLocalPreview() {
        if (mTXLivePusher != null) {
            mTXLivePusher.setPushListener(null);
            mTXLivePusher.stopCameraPreview(true);
            mTXLivePusher.stopPusher();
        }

        unInitLivePusher();
    }

    /**
     * 启动远端视频
     * @param videoView 视频预览组件
     * @param pusherInfo 对应视频的成员
     */
    public void addRemoteView(final @NonNull TXCloudVideoView videoView, final @NonNull PusherInfo pusherInfo) {

        roomListenerCallback.printLog("[RTCRoom] 开始播放 UserID{%s}, URL{%s}",
                pusherInfo.userID, pusherInfo.accelerateURL);

        synchronized (this) {

            if (mPlayers.containsKey(pusherInfo.userID)){
                PlayerItem pusherPlayer = mPlayers.get(pusherInfo.userID);
                if (pusherPlayer.player.isPlaying()){
                    return;
                }else {
                    pusherPlayer = mPlayers.remove(pusherInfo.userID);
                    pusherPlayer.destroy();
                }
            }

            final TXLivePlayer player = new TXLivePlayer(mContext);

            videoView.setVisibility(View.VISIBLE);
            player.setPlayerView(videoView);
            player.enableHardwareDecode(true);

            PlayerItem pusherPlayer = new PlayerItem(videoView, pusherInfo, player);
            mPlayers.put(pusherInfo.userID, pusherPlayer);

            player.setPlayListener(new ITXLivePlayListener() {
                @Override
                public void onPlayEvent(int event, Bundle param) {
                    if (event == TXLiveConstants.PLAY_EVT_PLAY_END || event == TXLiveConstants.PLAY_ERR_NET_DISCONNECT){
                        if (mPlayers.containsKey(pusherInfo.userID)) {
                            PlayerItem item = mPlayers.remove(pusherInfo.userID);
                            if (item != null) {
                                item.destroy();
                            }
                        }
                        // 刷新下pushers
                        onMemberChanged();
                    }
                }

                @Override
                public void onNetStatus(Bundle status) {

                }
            });

            int result = player.startPlay(pusherInfo.accelerateURL, TXLivePlayer.PLAY_TYPE_LIVE_RTMP_ACC);
            if (result != 0){
                roomListenerCallback.onDebugLog(String.format("[RTCRoom] 播放成员 {%s} 地址 {%s} 失败",
                        pusherInfo.userID, pusherInfo.accelerateURL));
            }
        }
    }

    /**
     * 停止远端视频
     * @param pusherInfo 对应视频的成员
     */
    public void delRemoteView(final @NonNull PusherInfo pusherInfo) {
        roomListenerCallback.printLog("[RTCRoom] 停止播放 UserID{%s}, URL{%s}", pusherInfo.userID, pusherInfo.accelerateURL);

        synchronized (this){
            if (!mPlayers.containsKey(pusherInfo.userID)){
                roomListenerCallback.onDebugLog("[RTCRoom] 没有用户 PusherInfo = "+ pusherInfo.userID);
                return;
            }

            PlayerItem pusherPlayer = mPlayers.remove(pusherInfo.userID);
            pusherPlayer.destroy();
        }
    }

    /**
     * 从前台切换到后台，关闭采集摄像头数据，推送默认图片
     */
    public void switchToBackground(){
        roomListenerCallback.onDebugLog("[RTCRoom] onPause");

        if (mTXLivePusher != null && mTXLivePusher.isPushing()) {
            mTXLivePusher.pausePusher();
        }

        for (Map.Entry<String, PlayerItem> entry : mPlayers.entrySet()) {
            entry.getValue().pause();
        }
    }

    /**
     * 由后台切换到前台，开启摄像头数据采集
     */
    public void switchToForeground(){
        roomListenerCallback.onDebugLog("[RTCRoom] onResume");

        if (mTXLivePusher != null && mTXLivePusher.isPushing()) {
            mTXLivePusher.resumePusher();
        }

        for (Map.Entry<String, PlayerItem> entry : mPlayers.entrySet()) {
            entry.getValue().resume();
        }
    }

    /**
     * 切换摄像头
     */
    public void switchCamera() {
        if (mTXLivePusher != null) {
            mTXLivePusher.switchCamera();
        }
    }

    /**
     * 静音设置
     * @param isMute 静音变量, true 表示静音， 否则 false
     */
    public void setMute(boolean isMute) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setMute(isMute);
        }
    }

    /**
     * 设置高清音频
     * @param enable true 表示启用高清音频（48K采样），否则 false（16K采样）
     */
    public void setHDAudio(boolean enable) {
        if (mTXLivePusher != null) {
            TXLivePushConfig config = mTXLivePusher.getConfig();
            config.setAudioSampleRate(enable ? 48000 : 16000);
            mTXLivePusher.setConfig(config);
        }
    }

    /**
     * 设置视频分辨率
     * @param resolution 视频分辨率参数值
     */
    public void setVideoResolution(int resolution) {
        if (mTXLivePusher != null) {
            TXLivePushConfig config = mTXLivePusher.getConfig();
            config.setVideoResolution(resolution);
            mTXLivePusher.setConfig(config);
        }
    }

    /**
     * 设置美颜效果.
     * @param style          美颜风格.三种美颜风格：0 ：光滑  1：自然  2：朦胧
     * @param beautyLevel    美颜等级.美颜等级即 beautyLevel 取值为0-9.取值为0时代表关闭美颜效果.默认值:0,即关闭美颜效果.
     * @param whiteningLevel 美白等级.美白等级即 whiteningLevel 取值为0-9.取值为0时代表关闭美白效果.默认值:0,即关闭美白效果.
     * @param ruddyLevel     红润等级.美白等级即 ruddyLevel 取值为0-9.取值为0时代表关闭美白效果.默认值:0,即关闭美白效果.
     * @return               是否成功设置美白和美颜效果. true:设置成功. false:设置失败.
     */
    public boolean setBeautyFilter(int style, int beautyLevel, int whiteningLevel, int ruddyLevel) {
        if (mTXLivePusher != null) {
            return mTXLivePusher.setBeautyFilter(style, beautyLevel, whiteningLevel, ruddyLevel);
        }
        return false;
    }

    /**
     * 调整摄像头焦距
     * @param  value 焦距，取值 0~getMaxZoom();
     * @return  true : 成功 false : 失败
     */
    public boolean setZoom(int value) {
        if (mTXLivePusher != null) {
            return mTXLivePusher.setZoom(value);
        }
        return false;
    }

    /**
     * 设置播放端水平镜像与否(tips：推流端前置摄像头默认看到的是镜像画面，后置摄像头默认看到的是非镜像画面)
     * @param enable true:播放端看到的是镜像画面,false:播放端看到的是镜像画面
     */
    public boolean setMirror(boolean enable) {
        if (mTXLivePusher != null) {
            return mTXLivePusher.setMirror(enable);
        }
        return false;
    }

    /**
     * 调整曝光
     * @param value 曝光比例，表示该手机支持最大曝光调整值的比例，取值范围从-1到1。
     *              负数表示调低曝光，-1是最小值，对应getMinExposureCompensation。
     *              正数表示调高曝光，1是最大值，对应getMaxExposureCompensation。
     *              0表示不调整曝光
     */
    public void setExposureCompensation(float value) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setExposureCompensation(value);
        }
    }

    /**
     * 设置麦克风的音量大小.
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     * @param x: 音量大小,1为正常音量,建议值为0~2,如果需要调大音量可以设置更大的值.
     * @return 是否成功设置麦克风的音量大小. true:设置麦克风的音量成功. false:设置麦克风的音量失败.
     */
    public boolean setMicVolume(float x) {
        if (mTXLivePusher != null) {
            return mTXLivePusher.setMicVolume(x);
        }
        return false;
    }

    /**
     * 设置背景音乐的音量大小.
     * <p>该接口用于混音处理,比如将背景音乐与麦克风采集到的声音混合后播放.
     * @param x 音量大小,1为正常音量,建议值为0~2,如果需要调大背景音量可以设置更大的值.
     * @return  是否成功设置背景音乐的音量大小. true:设置背景音的音量成功. false:设置背景音的音量失败.
     */
    public boolean setBGMVolume(float x) {
        if (mTXLivePusher != null) {
            return mTXLivePusher.setBGMVolume(x);
        }
        return false;
    }

    /**
     * 设置图像渲染角度.
     * @param rotation 图像渲染角度.
     */
    public void setRenderRotation(int rotation) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setRenderRotation(rotation);
        }
    }

    /**
     * setFilterImage 设置指定素材滤镜特效
     * @param bmp: 指定素材，即颜色查找表图片。注意：一定要用png图片格式！！！
     *           demo用到的滤镜查找表图片位于RTMPAndroidDemo/app/src/main/res/drawable-xxhdpi/目录下。
     */
    public void setFilter(Bitmap bmp) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setFilter(bmp);
        }
    }

    public void setMotionTmpl(String specialValue) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setMotionTmpl(specialValue);
        }
    }

    public boolean setGreenScreenFile(String file) {
        if (mTXLivePusher != null) {
            return mTXLivePusher.setGreenScreenFile(file);
        }
        return false;
    }

    public void setEyeScaleLevel(int level) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setEyeScaleLevel(level);
        }
    }

    public void setFaceSlimLevel(int level) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setFaceSlimLevel(level);
        }
    }

    public void setFaceVLevel(int level) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setFaceVLevel(level);
        }
    }

    public void setSpecialRatio(float ratio) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setSpecialRatio(ratio);
        }
    }

    public void setFaceShortLevel(int level) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setFaceShortLevel(level);
        }
    }

    public void setChinLevel(int scale) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setChinLevel(scale);
        }
    }

    public void setNoseSlimLevel(int scale) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setNoseSlimLevel(scale);
        }
    }

    public void setVideoQuality(int quality, boolean adjustBitrate, boolean adjustResolution) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setVideoQuality(quality, adjustBitrate, adjustResolution);
        }
    }

    public void setReverb(int reverbType) {
        if (mTXLivePusher != null) {
            mTXLivePusher.setReverb(reverbType);
        }
    }

    /**
     * 设置从前台切换到后台，推送的图片
     * @param bitmap
     */
    public void setPauseImage(final @Nullable Bitmap bitmap) {
        if (mTXLivePusher != null) {
            TXLivePushConfig config = mTXLivePusher.getConfig();
            config.setPauseImg(bitmap);
            config.setPauseFlag(TXLiveConstants.PAUSE_FLAG_PAUSE_VIDEO | TXLiveConstants.PAUSE_FLAG_PAUSE_AUDIO);
            mTXLivePusher.setConfig(config);
        }
    }

    /**
     * 从前台切换到后台，关闭采集摄像头数据
     * @param id 设置默认显示图片的资源文件
     */
    public void setPauseImage(final @IdRes int id){
        Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(), id);
        if (mTXLivePusher != null) {
            TXLivePushConfig config = mTXLivePusher.getConfig();
            config.setPauseImg(bitmap);
            config.setPauseFlag(TXLiveConstants.PAUSE_FLAG_PAUSE_VIDEO | TXLiveConstants.PAUSE_FLAG_PAUSE_AUDIO);
            mTXLivePusher.setConfig(config);
        }
    }

    /**
     * 设置视频的码率区间
     * @param minBitrate
     * @param maxBitrate
     */
    public void setBitrateRange(int minBitrate, int maxBitrate) {
        if (mTXLivePusher != null) {
            TXLivePushConfig config = mTXLivePusher.getConfig();
            config.setMaxVideoBitrate(maxBitrate);
            config.setMinVideoBitrate(minBitrate);
            mTXLivePusher.setConfig(config);
        }
    }

    /**
     * 设置推流端视频的分辨率
     */
    public void setVideoRatio(int videoRatio) {
        if (mTXLivePusher != null) {
            TXLivePushConfig config = mTXLivePusher.getConfig();
            if (videoRatio == RTCROOM_VIDEO_RATIO_9_16) {
                config.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_360_640);
            }
            else if (videoRatio == RTCROOM_VIDEO_RATIO_3_4) {
                config.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_480_640);
            }
            else if (videoRatio == RTCROOM_VIDEO_RATIO_1_1) {
                config.setVideoResolution(TXLiveConstants.VIDEO_RESOLUTION_TYPE_480_480);
            }
            mTXLivePusher.setConfig(config);
        }
    }

    @Override
    public void onConnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                roomListenerCallback.printLog("[IM] online");
            }
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                roomListenerCallback.printLog("[IM] offline");
                if (mRoomManager.isState(RoomState.Jioned)){
                    // TODO: 2017/11/13 重新初始化IM ??
                }
            }
        });
    }

    @Override
    public void onMemberChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                roomListenerCallback.printLog("[RTCRoom] onMemberChanged called");
                mRoomManager.updatePushers(roomListenerCallback);
            }
        });
    }

    @Override
    public void onGroupDestroyed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                roomListenerCallback.onDebugLog("[RTCRoom] onGroupDestroyed called ");
                mRoomManager.setState(RoomState.Absent);
                roomListenerCallback.onRoomClosed(mRoomManager.getRoomId());
            }
        });
    }

    @Override
    public void onGroupMessage(final String senderId, final String userName, final String headPic, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                PusherInfo pusherInfo = null;
                if (mRoomManager.mPushers.containsKey(senderId)) {
                    pusherInfo = mRoomManager.mPushers.get(senderId);
                }
                if (pusherInfo != null) {
                    roomListenerCallback.onRecvRoomTextMsg(mRoomManager.roomId, pusherInfo.userID, pusherInfo.userName, pusherInfo.userAvatar, message);
                }
            }
        });
    }

    @Override
    public void onDebugLog(final String line) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                roomListenerCallback.onDebugLog(line);
            }
        });
    }

    private void initLivePusher() {
        if (mTXLivePusher == null) {
            TXLivePushConfig config = new TXLivePushConfig();
            config.setPauseFlag(TXLiveConstants.PAUSE_FLAG_PAUSE_VIDEO | TXLiveConstants.PAUSE_FLAG_PAUSE_AUDIO);
            mTXLivePusher = new TXLivePusher(this.mContext);
            mTXLivePusher.setConfig(config);
            mTXLivePusher.setBeautyFilter(TXLiveConstants.BEAUTY_STYLE_SMOOTH, 5, 3, 2);
            mTXLivePusher.setVideoQuality(TXLiveConstants.VIDEO_QUALITY_REALTIEM_VIDEOCHAT, true, true);

            mTXLivePushListener = new TXLivePushListenerImpl();
            mTXLivePusher.setPushListener(mTXLivePushListener);
        }
    }

    private void unInitLivePusher() {
        if (mTXLivePusher != null) {
            mTXLivePushListener = null;
            mTXLivePusher.setPushListener(null);
            mTXLivePusher.stopCameraPreview(true);
            mTXLivePusher.stopPusher();
            mTXLivePusher = null;
        }
    }

    private interface PusherStreamCallback {
        void onError(int errCode, String errInfo);
        void onSuccess();
    }

    private void startPushStream(final String url, final PusherStreamCallback callback){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mTXLivePushListener != null) {
                    if (mTXLivePushListener.cameraEnable() == false) {
                        callback.onError(-1, "获取摄像头权限失败，请前往隐私-相机设置里面打开应用权限");
                        return;
                    }
                    if (mTXLivePushListener.micEnable() == false) {
                        callback.onError(-1, "获取摄像头权限失败，请前往隐私-相机设置里面打开应用权限");
                        return;
                    }
                }
                if (mTXLivePusher != null) {
                    roomListenerCallback.printLog("[RTCRoom] 开始推流 PushUrl {%s}", url);
                    mTXLivePushListener.setCallback(callback);
                    mTXLivePusher.startPusher(url);
                }
            }
        });
    }

    private void doCreateRoom(final String pushURL, final CreateRoomCallback callback){
        final String userId = mRoomManager.getSelfUserID();
        final String userName = mRoomManager.getSelfUserName();
        final String avatarUrl = mRoomManager.getAvatarUrl();
        final String roomName = mRoomManager.getRoomName();

        mHttpRequest.createRoom(userId, roomName, userName, avatarUrl, pushURL, new HttpRequests.OnResponseCallback<HttpResponse.CreateRoom>() {
                    @Override
                    public void onResponse(int retcode, @Nullable String retmsg, @Nullable HttpResponse.CreateRoom data) {
                        if (retcode != HttpResponse.CODE_OK || data == null || data.roomID == null) {
                            roomListenerCallback.onDebugLog("[RTCRoom] 创建会话错误： " + retmsg);
                            callback.onError(retcode, retmsg);
                        } else {

                            mRoomManager.setState(RoomState.Created | RoomState.Empty);
                            mRoomManager.setRoomId(data.roomID);
                            mRoomManager.setRoomName(roomName);

                            roomListenerCallback.printLog("[RTCRoom] 创建会话 ID{%s} 成功 ", data.roomID);
                            callback.onSuccess(data.roomID);
                        }
                    }//onResponse
                });
    }

    private interface JoinGroupCallback {
        void onError(int errCode, String errInfo);
        void onSuccess();
    }

    private void jionGroup(final String roomID, final JoinGroupCallback callback){
        mIMMessageMgr.jionGroup(roomID, new IMMessageMgr.Callback() {
            @Override
            public void onError(int code, String errInfo) {
                callback.onError(code, errInfo);
            }

            @Override
            public void onSuccess(Object... args) {
                callback.onSuccess();
            }
        });
    }

    private interface AddPusherCallback {
        void onError(int errCode, String errInfo);
        void onSuccess();
    }

    private void addPusher(final String roomID, final String pushURL, final AddPusherCallback callback) {
        mHttpRequest.addPusher(roomID,
                mRoomManager.getSelfUserID(),
                mRoomManager.getSelfUserName(),
                mRoomManager.getAvatarUrl(),
                pushURL, new HttpRequests.OnResponseCallback<HttpResponse>() {
                    @Override
                    public void onResponse(int retcode, @Nullable String retmsg, @Nullable HttpResponse data) {
                        if (retcode == HttpResponse.CODE_OK) {
                            roomListenerCallback.printLog("[RTCRoom] Enter Room 成功");
                            callback.onSuccess();
                        } else {
                            roomListenerCallback.printLog("[RTCRoom] Enter Room 失败");
                            callback.onError(retcode, retmsg);
                        }
                    }
                });
    }

    private void runOnUiThread(final Runnable runnable){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                runnable.run();
            }
        });
    }

    private final class PlayerItem {
        public TXCloudVideoView view;
        public PusherInfo pusher;
        public TXLivePlayer player;

        public PlayerItem(TXCloudVideoView view, PusherInfo pusher, TXLivePlayer player) {
            this.view = view;
            this.pusher = pusher;
            this.player = player;
        }

        public void resume(){
            this.player.resume();
        }

        public void pause(){
            this.player.pause();
        }

        public void destroy(){
            this.player.stopPlay(true);
            this.view.onDestroy();
        }
    }


    private class RoomState {
        public static final int Absent  = 0;            //会话不存在
        public static final int Empty   = 1;            //会话存在，但没有成员
        public static final int Created = 2;            //我创建了会话
        public static final int Jioned  = 4;            //我加入了会话
    }

    private class RoomManager {

        private HashMap<String, PusherInfo> mPushers = new LinkedHashMap<>();
        private String roomId;
        private String roomName;
        private String selfUserID;
        private String selfUserName;
        private String avatarUrl;
        private int roomState = RoomState.Absent;

        public boolean isDestroyed() {
            return roomState == RoomState.Absent;
        }

        public void setState(int state) {
            roomState = state;
        }

        public int getState() {
            return roomState;
        }

        public boolean isState(int state){
            return (state & roomState) == state;
        }

        public String getAvatarUrl() {
            return avatarUrl == null ? "null" : avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public String getRoomId() {
            return roomId;
        }

        public synchronized void setRoomId(String roomId) {
            this.roomId = roomId;
        }

        public String getRoomName() {
            return roomName == null ? "未命名" : roomName;
        }

        public synchronized void setRoomName(String roomName) {
            this.roomName = roomName;
        }

        public String getSelfUserID() {
            return selfUserID;
        }

        public synchronized void setSelfUserID(String selfUserID) {
            this.selfUserID = selfUserID;
        }

        public void setSelfUserName(String selfUserName) {
            this.selfUserName = selfUserName;
        }

        public String getSelfUserName() {
            return selfUserName == null ? "null" : selfUserName;
        }

        public synchronized void clean() {
            roomId = "";
            roomName = "";
            roomState = RoomState.Absent;
            mPushers.clear();
        }

        public synchronized void mergerPushers(List<PusherInfo> members, List<PusherInfo> newMembers, List<PusherInfo> delMembers){

            if (members == null) {
                if (delMembers != null) {
                    delMembers.clear();
                    for (Map.Entry<String, PusherInfo> entry : mPushers.entrySet()) {
                        delMembers.add(entry.getValue());
                    }
                }
                mPushers.clear();
                return;
            }

            HashMap<String, PusherInfo> memberHashMap = new LinkedHashMap<>();

            for (PusherInfo member : members) {
                if (member.userID != null && !member.userID.equals(selfUserID)){
                    if (!mPushers.containsKey(member.userID)) {
                        if (newMembers != null)
                            newMembers.add(member);
                    }
                    memberHashMap.put(member.userID, member);
                }
            }

            if (delMembers != null) {
                for (Map.Entry<String, PusherInfo> entry : mPushers.entrySet()) {
                    if (!memberHashMap.containsKey(entry.getKey())) {
                        delMembers.add(entry.getValue());
                    }
                }
            }

            this.mPushers.clear();
            this.mPushers = memberHashMap;
        }

        private void updatePushers(final IRTCRoomListener callback){

            if (roomId == null) return;

            mHttpRequest.getPushers(roomId, new HttpRequests.OnResponseCallback<HttpResponse.PusherList>() {
                @Override
                public void onResponse(int retcode, @Nullable String retmsg, @Nullable final HttpResponse.PusherList data) {
                    if (retcode == HttpResponse.CODE_OK && data != null && data.pushers != null) {

                        List<PusherInfo> newMembers = new ArrayList<>();
                        List<PusherInfo> delMembers = new ArrayList<>();
                        mergerPushers(data.pushers, newMembers, delMembers);

                        roomListenerCallback.printLog("[RTCRoom][updatePushers] pushers.size  All(%d), new(%d), remove(%d)",
                                data.pushers.size(), newMembers.size(), delMembers.size());

                        for (PusherInfo member : delMembers) {
                            callback.onPusherQuit(member);
                        }

                        for (PusherInfo member : newMembers) {
                            callback.onPusherJoin(member);
                        }
                    }
                }
            });
        }
    }

    private class HeartBeatThread extends HandlerThread {
        private Handler handler;
        private boolean stopHeartbeat = false;

        public HeartBeatThread() {
            super("RTCHeartBeatThread");
            this.start();
            handler = new Handler(this.getLooper());
        }

        private Runnable heartBeatRunnable = new Runnable() {
            @Override
            public void run() {
                boolean b = mHttpRequest.heartBeat(mRoomManager.selfUserID, mRoomManager.roomId);
                if (b || !stopHeartbeat){
                    handler.postDelayed(heartBeatRunnable, 5000);
                }
                stopHeartbeat = false;
            }
        };

        private void startHeartbeart(){
            stopHeartbeat();
            roomListenerCallback.printLog("start userID {%s} heartbeat", mRoomManager.getSelfUserID());
            handler.postDelayed(heartBeatRunnable, 1000);
        }

        private void stopHeartbeat(){
            roomListenerCallback.printLog("stop userID {%s} heartbeat", mRoomManager.getSelfUserID());
            stopHeartbeat = true;
            handler.removeCallbacks(heartBeatRunnable);
        }
    }

    private class RoomListenerCallback implements IRTCRoomListener {

        private final Handler handler;
        private IRTCRoomListener roomMemberEventListener;

        public RoomListenerCallback(IRTCRoomListener roomMemberEventListener) {
            this.roomMemberEventListener = roomMemberEventListener;
            handler = new Handler(Looper.getMainLooper());
        }


        public void setRoomMemberEventListener(IRTCRoomListener roomMemberEventListener) {
            this.roomMemberEventListener = roomMemberEventListener;
        }

        @Override
        public void onPusherJoin(final PusherInfo member) {
            printLog("[RTCRoom] onPusherJoin, UserID {%s} PlayUrl {%s}", member.userID, member.accelerateURL);
            new Exception("-----RTCRoom->onPusherJoin------").printStackTrace();
            if (roomMemberEventListener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        roomMemberEventListener.onPusherJoin(member);
                    }
                });
            }
        }

        @Override
        public void onPusherQuit(final PusherInfo member) {
            printLog("[RTCRoom] onPusherQuit, UserID {%s} PlayUrl {%s}", member.userID, member.accelerateURL);
            if(roomMemberEventListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        roomMemberEventListener.onPusherQuit(member);
                    }
                });
        }

        @Override
        public void onRoomClosed(final String roomId) {
            printLog("[RTCRoom] onRoomClosed, RoomId {%s}", roomId);
            if(roomMemberEventListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        roomMemberEventListener.onRoomClosed(roomId);
                    }
                });
        }

        void printLog(String format, Object ...args){
            String line = String.format(format, args);
            onDebugLog(line);
        }
        @Override
        public void onDebugLog(final String line) {
            if(roomMemberEventListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        roomMemberEventListener.onDebugLog(line);
                    }
                });
        }

        @Override
        public void onGetPusherList(final List<PusherInfo> pusherInfoList) {
            if(roomMemberEventListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        roomMemberEventListener.onGetPusherList(pusherInfoList);
                    }
                });
        }

        @Override
        public void onRecvRoomTextMsg(final String roomId, final String userId, final String userName, final String headPic, final String message) {
            if(roomMemberEventListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        roomMemberEventListener.onRecvRoomTextMsg(roomId, userId, userName, headPic, message);
                    }
                });
        }

        @Override
        public void onError(final int errorCode, final String errorMessage) {
            if(roomMemberEventListener != null)
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        roomMemberEventListener.onError(errorCode, errorMessage);
                    }
                });
        }
    }

    private class MainCallback<C, T> {

        private C callback;

        MainCallback(C callback) {
            this.callback = callback;
        }

        void onError(final int errCode, final String errInfo) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Method onError = callback.getClass().getMethod("onError", int.class, String.class);
                        onError.invoke(callback, errCode, errInfo);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        void onSuccess(final T obj) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Method onSuccess = callback.getClass().getMethod("onSuccess", obj.getClass());
                                onSuccess.invoke(callback, obj);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            });
        }

        void onSuccess() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Method onSuccess = callback.getClass().getMethod("onSuccess");
                                onSuccess.invoke(callback);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            });
        }
    }

    private class TXLivePushListenerImpl implements ITXLivePushListener {
        private boolean mCameraEnable = true;
        private boolean mMicEnable = true;
        private PusherStreamCallback mCallback = null;

        public void setCallback(PusherStreamCallback callback) {
            mCallback = callback;
        }

        public boolean cameraEnable() {
            return mCameraEnable;
        }

        public boolean micEnable() {
            return mMicEnable;
        }

        @Override
        public void onPushEvent(int event, Bundle param) {
            if (event == TXLiveConstants.PUSH_EVT_PUSH_BEGIN) {
                roomListenerCallback.onDebugLog("[RTCRoom] 推流成功");
                if (mCallback != null) {
                    mCallback.onSuccess();
                }
            } else if (event == TXLiveConstants.PUSH_ERR_OPEN_CAMERA_FAIL) {
                mCameraEnable = false;
                roomListenerCallback.onDebugLog("[RTCRoom] 推流失败：打开摄像头失败");
                if (mCallback != null) {
                    mCallback.onError(-1, "获取摄像头权限失败，请前往隐私-相机设置里面打开应用权限");
                }
                else {
                    roomListenerCallback.onError(-1, "获取摄像头权限失败，请前往隐私-相机设置里面打开应用权限");
                }
            } else if (event == TXLiveConstants.PUSH_ERR_OPEN_MIC_FAIL) {
                mMicEnable = false;
                roomListenerCallback.onDebugLog("[RTCRoom] 推流失败：打开麦克风失败");
                if (mCallback != null) {
                    mCallback.onError(-1, "获取麦克风权限失败，请前往隐私-麦克风设置里面打开应用权限");
                }
                else {
                    roomListenerCallback.onError(-1, "获取麦克风权限失败，请前往隐私-麦克风设置里面打开应用权限");
                }
            } else if (event == TXLiveConstants.PUSH_ERR_NET_DISCONNECT) {
                roomListenerCallback.onDebugLog("[LiveRoom] 推流失败：网络断开");
                roomListenerCallback.onError(-1, "网络断开，推流失败");
            }
        }

        @Override
        public void onNetStatus(Bundle status) {

        }
    }
}

package com.tencent.liteav.demo.videocall;


import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.view.ViewPager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.avroom.TXCAVRoom;
import com.tencent.avroom.TXCAVRoomCallback;
import com.tencent.avroom.TXCAVRoomConfig;
import com.tencent.avroom.TXCAVRoomConstants;
import com.tencent.avroom.TXCAVRoomLisenter;
import com.tencent.avroom.TXCAVRoomParam;
import com.tencent.liteav.basic.log.TXCLog;
import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.common.widget.BeautySettingPannel;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.tencent.liteav.demo.R.id.btnLog;
import static com.tencent.liteav.demo.common.widget.BeautySettingPannel.ITEM_TYPE_BEAUTY;

public class VideoCallActivity extends Activity implements View.OnClickListener, BeautySettingPannel.IOnBeautyParamsChangeListener {
    private TXCloudVideoView mCaptureView;
    private EditText mRoomId;
    private TXCAVRoom mAVRoom;
    private Button mPlayBtn, mSwiCamBtn, mLogBtn, mBeautyBtn, mRenderBtn, mMicBtn, mPushBtn;
    private TextView mBackBtn;
    private final static String TAG = VideoCallActivity.class.getSimpleName();
    private FrameLayout mRemoteViewLayer;
    private BeautySettingPannel mBeautyPannelView;
    private ArrayList<RemoteCloudVideoView> mRemoteViews = new ArrayList();
    private boolean isInRoom = false;
    protected StringBuffer mLogMsg = new StringBuffer("");
    private final int mLogMsgLenLimit = 3000;
    private long mMyUserId;
    private int appId = 1400044820;
    private ViewPager mViewpager;
    private int mBeautyLevel = 0;
    private int mWhiteningLevel = 0;
    private int mBeautyStyle = TXLiveConstants.BEAUTY_STYLE_SMOOTH;
    private TXCAVRoomConfig mAvRoomConfig;
    private boolean mFrontCamera = true;
    private boolean hasVideo = true;
    private boolean mLocalMute = false;
    private boolean mPlayerFullScreen = true;
    private int mVideoMemNum = 0;
    private MainPagerAdapter mPageAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_videocall);
        checkPermission();
        initView();

        mMyUserId = Math.abs(new Random().nextInt()); //ID取随机数
        TXCLog.i(TAG, "KeyWay onCreate: myid " + mMyUserId + " appId " + appId);

        mAvRoomConfig = new TXCAVRoomConfig();
        Bitmap pauseImg = BitmapFactory.decodeResource(getResources(), R.drawable.pause_publish);
        mAvRoomConfig.pauseImg(pauseImg);
        mAvRoomConfig.setPauseFlag(TXLiveConstants.PAUSE_FLAG_PAUSE_VIDEO|TXLiveConstants.PAUSE_FLAG_PAUSE_AUDIO);
        mAVRoom = new TXCAVRoom(this, mAvRoomConfig, mMyUserId, appId);
        mAVRoom.setAvRoomLisenter(new TXCAVRoomLisenter() {//AVRoom回调接口
            @Override
            public void onVideoStateChange(long userID, boolean isEnter) { //
                //视频成员进出
                TXCLog.i(TAG, "timeway onVideoStateChange: " + userID + " isEnter  " + isEnter);
                if (userID == mMyUserId) {
                    return;
                }
                if (isEnter) {
                    mVideoMemNum++;
                    addRemoteView(userID);
                    mAVRoom.startRemoteView(findAvailableView(userID), userID); //开启远端视频渲染
                    updateViewLayout();
                } else {//退出取消播放器
                    mVideoMemNum--;
                    mAVRoom.stopRemoteView(userID);//关闭远端视频渲染
                    deleteRemoteView(userID);//移除远端视频窗口
                    updateViewLayout();
                }
                updatePushVideoParam(mVideoMemNum);

                synchronized (this) {
                    if (isEnter) {
                        addLogView(userID);
                    } else {
                        removeLogView(userID);
                    }
                }
            }

            @Override
            public void onMemberChange(long userID, boolean isEnter) {
                //成员上线下线
                TXCLog.i(TAG, "timeway onMemberChange: " + userID);

            }

            @Override
            public void onAVRoomEvent(long userID, int event, Bundle bundle) { //用户事件回抛
                TXCLog.i(TAG, "onAVRoomEvent: " + userID + "  event  " + event);
                if (userID == 0) return;
                appendEventLog(userID, event, bundle);
                TextView eventView = findLogEventView(userID);
                ScrollView scrollView = findScrollView(userID);
                if (eventView != null && scrollView != null) {
                    eventView.setText(mLogMsg);
                    scroll2Bottom(scrollView, eventView);
                }


                if (event == TXCAVRoomConstants.AVROOM_WARNING_DISCONNECT) { //重练失败 退房
                    mAVRoom.exitRoom(new TXCAVRoomCallback() {
                        @Override
                        public void onComplete(int result) {
                            Toast.makeText(VideoCallActivity.this, " quit onComplete: " + result, Toast.LENGTH_SHORT).show();
                            TXCLog.i(TAG, "keyway exitroom onComplete: " + result);
                            mPlayBtn.setBackgroundResource(R.drawable.play_start);
                            isInRoom = false;
                            mVideoMemNum = 0;
                            clearAllLogView();
                            clearViews();

                        }
                    });
                }

            }

            @Override
            public void onAVRoomStatus(long userID, Bundle netdata) { //网络信息回抛
                TXCLog.i(TAG, "onAVRoomStatus: " + netdata.size());
                TextView statusView = findLogStatusView(userID);
                if (statusView != null) {
                    statusView.setMovementMethod(new ScrollingMovementMethod());
                    statusView.setText(getNetStatusString("" + userID, netdata));
                }
            }
        });

        TextView titleTV = (TextView) findViewById(R.id.title_tv);
        titleTV.setText(getIntent().getStringExtra("TITLE"));
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void initView() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mRemoteViewLayer = (FrameLayout) findViewById(R.id.remoteViews);
        mCaptureView = (TXCloudVideoView) findViewById(R.id.local_view);

        mViewpager = (ViewPager) findViewById(R.id.viewpager);//Log界面
        mPageAdapter = new MainPagerAdapter();
        mViewpager.setAdapter(mPageAdapter);
        mViewpager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                Log.i(TAG, "onPageSelected: " + mPageAdapter.getView(position).getTag());
                showRemoteCloudViewTag((long) mPageAdapter.getView(position).getTag());
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        //美颜p图部分
        mBeautyPannelView = (BeautySettingPannel) findViewById(R.id.layoutFaceBeauty);
        mBeautyPannelView.initProgressValue(ITEM_TYPE_BEAUTY, 0, 0);    // 美颜设置为0
        mBeautyPannelView.initProgressValue(ITEM_TYPE_BEAUTY, 1, 0);    // 美白设置为0
        mBeautyPannelView.initProgressValue(ITEM_TYPE_BEAUTY, 2, 0);    // 红润设置为0
        mBeautyPannelView.setBeautyParamsChangeListener(this);


        mPlayBtn = (Button) findViewById(R.id.btnPlay);
        mPlayBtn.setOnClickListener(this);
        mSwiCamBtn = (Button) findViewById(R.id.btnCameraChange);
        mSwiCamBtn.setOnClickListener(this);
        mBackBtn = (TextView) findViewById(R.id.back_tv);
        mBackBtn.setOnClickListener(this);
        mRoomId = (EditText) findViewById(R.id.roomid);
        mRoomId.setHint("请输入房间号");
        mBeautyBtn = (Button) findViewById(R.id.btnFaceBeauty);
        mBeautyBtn.setOnClickListener(this);
        mRenderBtn = (Button) findViewById(R.id.btnRenderMode);
        mRenderBtn.setOnClickListener(this);
        mMicBtn = (Button) findViewById(R.id.muteMic);
        mMicBtn.setOnClickListener(this);
        mPushBtn = (Button) findViewById(R.id.muteVideo);
        mPushBtn.setOnClickListener(this);
        //log部分
        mLogBtn = (Button) findViewById(btnLog);
        mLogBtn.setOnClickListener(this);
    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        TXCLog.i(TAG, "onStop: ");
    }

    @Override
    protected void onDestroy() {
        TXCLog.i(TAG, "onDestroy: ");
        if (mAVRoom != null)
            mAVRoom.destory();
        super.onDestroy();
    }

    void checkPermission() {
        final List<String> permissionsList = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if ((checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED))
                permissionsList.add(Manifest.permission.CAMERA);
            if ((checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED))
                permissionsList.add(Manifest.permission.RECORD_AUDIO);
            if ((checkSelfPermission(Manifest.permission.WAKE_LOCK) != PackageManager.PERMISSION_GRANTED))
                permissionsList.add(Manifest.permission.WAKE_LOCK);
            if ((checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED))
                permissionsList.add(Manifest.permission.MODIFY_AUDIO_SETTINGS);
            if ((checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED))
                permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if ((checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED))
                permissionsList.add(Manifest.permission.READ_PHONE_STATE);
            if (permissionsList.size() != 0) {
                requestPermissions(permissionsList.toArray(new String[permissionsList.size()]),
                        0);
            }
        }
    }


    private void addLogView(long id) {
        TXCLog.i(TAG, "addLogView " + id);
        if (isExistLogView(id) != null) return;
        LayoutInflater lf = getLayoutInflater().from(VideoCallActivity.this);
        View view = lf.inflate(R.layout.activity_video_logview, null);
        view.setTag(id);
//        viewContainter.add(view);
//        mPageAdapter.addView(view);

        mPageAdapter.addView(view);
        mPageAdapter.notifyDataSetChanged();
//        viewPageAddView(view);
    }

    private void removeLogView(long id) {
        View view = isExistLogView(id);
        if (view != null) {
            mPageAdapter.removeView(mViewpager, view);
            mPageAdapter.notifyDataSetChanged();
        }
    }

    private View isExistLogView(long userId) {
        for (int i = 0; i < mPageAdapter.getViewsList().size(); i++) {
            if ((long) mPageAdapter.getView(i).getTag() == userId) {
                return mPageAdapter.getView(i);
            }
        }
        return null;

    }

    private void clearAllLogView() {
        Iterator<View> iter = mPageAdapter.getViewsList().iterator();
        if (iter.hasNext()) {
            View view = iter.next();
            TextView logViewEvent = (TextView) view.findViewById(R.id.logViewEvent);
            logViewEvent.setText("");
            TextView logViewStatus = (TextView) view.findViewById(R.id.logViewStatus);
            logViewStatus.setText("");
            mPageAdapter.removeView(mViewpager, view);
            mPageAdapter.notifyDataSetChanged();
        }
    }


    private long roomId = -1;

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnPlay:
//                clearLog();
                clearAllLogView();
                if (isInRoom == false) {
                    //Roomid
                    roomId = -1;
                    try {
                        String strRoomId = mRoomId.getText().toString();
                        roomId = Long.valueOf(strRoomId);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (roomId != -1) {
                        synchronized (VideoCallActivity.this) {
                            if (hasVideo) {
                                mAVRoom.startLocalPreview(mCaptureView);//渲染本地数据
                            }
                            enterRoom(roomId); //进房

                        }
                    } else {
                        Toast.makeText(VideoCallActivity.this, "房间号非法", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    synchronized (VideoCallActivity.this) {
//                        if (mAVRoom.isInRoom()) //退房
                        mAVRoom.exitRoom(new TXCAVRoomCallback() {
                            @Override
                            public void onComplete(int result) {
                                Toast.makeText(VideoCallActivity.this, " quit onComplete: " + result, Toast.LENGTH_SHORT).show();
                                TXCLog.i(TAG, "keyway exitroom onComplete: " + result);
                                if (result == 0) {
                                    mPlayBtn.setBackgroundResource(R.drawable.play_start);
                                    isInRoom = false;
                                    clearAllLogView();
                                    clearViews();
                                }

                            }
                        });
                    }
                }
//
                break;
            case R.id.btnCameraChange:
                mFrontCamera = !mFrontCamera;
                if (mAVRoom.isPushing()) {
                    mAVRoom.switchCamera();
                }
                mAVRoom.getRoomConfig().frontCamera(mFrontCamera);
                mSwiCamBtn.setBackgroundResource(mFrontCamera ? R.drawable.camera_change : R.drawable.camera_change2);
                break;
            case R.id.back_tv:
                if (isInRoom)
                    mAVRoom.exitRoom(new TXCAVRoomCallback() {
                        @Override
                        public void onComplete(int result) {
                            Toast.makeText(VideoCallActivity.this, " quit onComplete: " + result, Toast.LENGTH_SHORT).show();
                            TXCLog.i(TAG, "keyway exitroom onComplete: " + result);
                            mPlayBtn.setBackgroundResource(R.drawable.play_start);
                            isInRoom = false;
                            clearAllLogView();
                            clearViews();
                            finish();

                        }
                    });
                else
                    finish();
                break;
            case R.id.btnLog:
                if (mViewpager.getVisibility() == View.INVISIBLE) {
                    mViewpager.setVisibility(View.VISIBLE);
                    mLogBtn.setBackgroundResource(R.drawable.log_hidden);
                } else {
                    mViewpager.setVisibility(View.INVISIBLE);
                    clearRemoteCloudViewTag();
                    mLogBtn.setBackgroundResource(R.drawable.log_show);
                }
                break;

            case R.id.btnFaceBeauty:
                boolean beautySetting = mBeautyPannelView.getVisibility() == View.VISIBLE;
                mBeautyPannelView.setVisibility(beautySetting ? View.GONE : View.VISIBLE);
                if (beautySetting) {
                    mBeautyBtn.setBackgroundResource(R.drawable.beauty);
                } else {
                    mBeautyBtn.setBackgroundResource(R.drawable.beauty_dis);
                }

                break;
            case R.id.btnRenderMode:
                mPlayerFullScreen = !mPlayerFullScreen;
                mRenderBtn.setText(mPlayerFullScreen ? "全屏" : "适应");
                if (mPlayerFullScreen) {
                    mAvRoomConfig.setRemoteRenderMode(TXCAVRoomConstants.RENDER_MODE_ADJUST_RESOLUTION);
                } else {
                    mAvRoomConfig.setRemoteRenderMode(TXCAVRoomConstants.RENDER_MODE_FULL_FILL_SCREEN);
                }
//                mAvRoomConfig.setRemoteScreenSettings(mPlayerFullScreen);


                if (mAVRoom.isInRoom()) {
                    if (mPlayerFullScreen == false)
                        mAVRoom.setRenderMode(TXLiveConstants.RENDER_MODE_ADJUST_RESOLUTION);
                    else
                        mAVRoom.setRenderMode(TXLiveConstants.RENDER_MODE_FULL_FILL_SCREEN);
                }
                break;

            case R.id.muteMic:
                if (mLocalMute == false) {
                    mAVRoom.setLocalMute(true);
                    mMicBtn.setBackgroundResource(R.drawable.mute_press);
                    mLocalMute = true;
                } else {
                    mAVRoom.setLocalMute(false);
                    mMicBtn.setBackgroundResource(R.drawable.mute);
                    mLocalMute = false;
                }
                break;

            case R.id.muteVideo:
                //进房前
                hasVideo = !hasVideo;
                mAvRoomConfig.setHasVideo(hasVideo);
                mPushBtn.setBackgroundResource(hasVideo ? R.drawable.off : R.drawable.off_press);
                //推流中
                if (mAVRoom.isPushing()) {
                    if (mAVRoom.getRoomConfig().isHasVideo() == false)//当前摄像头关闭状态
                    {
                        mAVRoom.stopLocalPreview();//关闭
                        updateViewLayout();
                    } else {
                        mAVRoom.startLocalPreview(mCaptureView);//打开
                        updateViewLayout();
                    }
                }

                break;
        }

    }


    OkHttpClient okHttpClient;

    /**
     * 获取房间授权
     *
     * @param myid   用户ID
     * @param appid  应用ID
     * @param roomid 房间ID
     */
    private void getAuth(final long myid, final int appid, final long roomid) {
        final String ipv4 = "119.29.173.130";
        final String ipv6 = mAVRoom.nat64Compatable("119.29.173.130", (short)8000);
        okHttpClient = new OkHttpClient();
        okHttpClient.newBuilder().connectTimeout(5, TimeUnit.SECONDS);
        Request request = new Request.Builder().url("http://" + (ipv4.equals(ipv6) ? ipv4 : ("[" + ipv6 + "]")) + ":8000/getKey?account=" + myid + "&appId=" + appid + "&authId=" + roomid + "&privilegeMap=-1").build();
        Call call = okHttpClient.newCall(request);
        TXCLog.i(TAG, "keyway getAuth  ");
        // 开启异步线程访问网络
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                TXCLog.i(TAG, "keyway getAuth onFailure " + e.toString());
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(VideoCallActivity.this, "获取授权失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final byte[] auth = response.body().bytes();
                TXCLog.i(TAG, "keyway getAuth onResponse " + response.toString());
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            mAVRoom.enterRoom(new TXCAVRoomParam(roomId).authBuffer(auth), new TXCAVRoomCallback() {
                                @Override
                                public void onComplete(int result) {
                                    Toast.makeText(VideoCallActivity.this, " create onComplete: " + result, Toast.LENGTH_SHORT).show();
                                    isInRoom = true;
                                    addLogView(mMyUserId);
                                    mVideoMemNum = 0;
                                    TXCLog.i(TAG, "updatePushVideoParam enterRoom " + mVideoMemNum);
                                    updatePushVideoParam(mVideoMemNum);
                                    mPlayBtn.setBackgroundResource(R.drawable.play_pause);

                            }
                        });
                    }
                });

            }

        });
    }

    /**
     * 进房
     *
     * @param roomId 房间ID
     */
    private void enterRoom(final long roomId) {
        getAuth(mMyUserId, appId, roomId);
    }


    private RemoteCloudVideoView findAvailableView(long userID) {
        TXCLog.i(TAG, "findAvailableView: " + mRemoteViews.size());
        if (mRemoteViews == null && mRemoteViews.size() == 0) return null;
        for (int i = 0; i < mRemoteViews.size(); i++) {
            if ((long) mRemoteViews.get(i).getTag() == userID) {
                TXCLog.i(TAG, "findAvailableView: " + userID);
                return mRemoteViews.get(i);
            }

        }
        return null;
    }

    private void showRemoteCloudViewTag(long userID) {
        if (mRemoteViews == null && mRemoteViews.size() == 0) return;
        for (int i = 0; i < mRemoteViews.size(); i++) {
            if ((long) mRemoteViews.get(i).getTag() == userID) {
                TXCLog.i(TAG, "findAvailableView: " + userID);
                mRemoteViews.get(i).showTag(true);
            } else {
                mRemoteViews.get(i).showTag(false);
            }

        }
    }

    private void clearRemoteCloudViewTag() {
        if (mRemoteViews == null && mRemoteViews.size() == 0) return;
        for (int i = 0; i < mRemoteViews.size(); i++) {
            mRemoteViews.get(i).showTag(false);

        }
    }

    @Override
    protected void onPause() {
        mAVRoom.onPause();
        super.onPause();
    }


    @Override
    protected void onResume() {
        mAVRoom.onResume();
        super.onResume();
    }

    private int mRuddyLevel = 0;

    @Override
    public void onBeautyParamsChange(BeautySettingPannel.BeautyParams params, int key) {
        switch (key) {
            case BeautySettingPannel.BEAUTYPARAM_EXPOSURE:
                mAVRoom.setExposureCompensation((params.mExposure));

                break;
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY:
                mBeautyLevel = params.mBeautyLevel;
                mAVRoom.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);

                break;
            case BeautySettingPannel.BEAUTYPARAM_WHITE:
                mWhiteningLevel = params.mWhiteLevel;
                mAVRoom.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                break;

            case BeautySettingPannel.BEAUTYPARAM_RUDDY:
                mRuddyLevel = params.mRuddyLevel;
                mAVRoom.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                break;
            case BeautySettingPannel.BEAUTYPARAM_BIG_EYE:
                mAVRoom.setEyeScaleLevel(params.mBigEyeLevel);
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACE_LIFT:
                mAVRoom.setFaceSlimLevel(params.mFaceSlimLevel);
                break;
            case BeautySettingPannel.BEAUTYPARAM_FILTER:
                mAVRoom.setFilter(params.mFilterBmp);
                break;
            case BeautySettingPannel.BEAUTYPARAM_GREEN:
                mAVRoom.setGreenScreenFile(params.mGreenFile);
                break;
            case BeautySettingPannel.BEAUTYPARAM_MOTION_TMPL:
                mAVRoom.setMotionTmpl(params.mMotionTmplPath);
                break;
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY_STYLE:
                mBeautyStyle = params.mBeautyStyle;
                if (mAVRoom != null) {
                    mAVRoom.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACEV:
                if (mAVRoom != null) {
                    mAVRoom.setFaceVLevel(params.mFaceVLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACESHORT:
                if (mAVRoom != null) {
                    mAVRoom.setFaceShortLevel(params.mFaceShortLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_CHINSLIME:
                if (mAVRoom != null) {
                    mAVRoom.setChinLevel(params.mChinSlimLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_NOSESCALE:
                if (mAVRoom != null) {
                    mAVRoom.setNoseSlimLevel(params.mNoseScaleLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FILTER_MIX_LEVEL:
                if (mAVRoom != null) {
                    mAVRoom.setSpecialRatio(params.mFilterMixLevel/10.f);
                }
                break;
        }
    }


    /**
     * 增加远端渲染
     *
     * @param userID 远端用户ID
     */
    private void addRemoteView(long userID) {
        TXCLog.i(TAG, "addRemoteView: id " + userID);
        mRemoteViewLayer.setVisibility(View.VISIBLE);
        RemoteCloudVideoView mRemoteView = new RemoteCloudVideoView(this);
        mRemoteView.setVisibility(View.INVISIBLE);
        mRemoteView.setTag(userID);
        mRemoteViewLayer.addView(mRemoteView);
        if (mRemoteViews != null && !mRemoteViews.contains(mRemoteView)) {
            mRemoteViews.add(mRemoteView);
        } else {
            TXCLog.i(TAG, "addRemoteView: null");
        }

    }


    /**
     * 移除远端渲染
     *
     * @param userID 远端用户ID
     */
    private void deleteRemoteView(long userID) {
        TXCLog.i(TAG, "deleteRemoteView: id " + userID);
        if (mRemoteViews == null) return;
        Iterator<RemoteCloudVideoView> iter = mRemoteViews.iterator();
        while (iter.hasNext()) {
            RemoteCloudVideoView view = iter.next();
            if ((long) view.getTag() == userID) {
                iter.remove();
                mRemoteViewLayer.removeView(view);
            }
        }

    }


    public void clearViews() {
        TXCLog.i(TAG, "findAvailableView: " + mRemoteViews.size());
        if (mRemoteViews == null) return;
        for (int i = 0; i < mRemoteViews.size(); i++) {
            mRemoteViews.get(i).setVisibility(View.INVISIBLE);
        }
        mRemoteViews.clear();
        mRemoteViewLayer.removeAllViews();
        mRemoteViewLayer.setVisibility(View.GONE);
        updateViewLayout();
    }


    /**
     * log显示辅助方法
     */
    protected void appendEventLog(long userID, int event, Bundle param) {
        String message = param.getString(TXCAVRoomConstants.EVT_DESCRIPTION);
        String str = "receive event: " + event + ", " + message;
        Log.d(TAG, str);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        String date = sdf.format(System.currentTimeMillis());
        while (mLogMsg.length() > mLogMsgLenLimit) {
            int idx = mLogMsg.indexOf("\n");
            if (idx == 0)
                idx = 1;
            mLogMsg = mLogMsg.delete(0, idx);
        }
        mLogMsg = mLogMsg.append("\n" + "[" + date + "]" + message);
    }


    //公用打印辅助方法
    protected String getNetStatusString(String UserID, Bundle status) {

        String str = String.format("%-14s \n \n%-14s %-14s %-12s\n%-8s %-8s %-8s %-8s\n%-14s %-8s %-8s\n%-14s %-14s",
                "ID:" + UserID,
                "CPU:" + status.getString(TXLiveConstants.NET_STATUS_CPU_USAGE),
                "RES:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_WIDTH) + "*" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_HEIGHT),
                "SPD:" + status.getInt(TXLiveConstants.NET_STATUS_NET_SPEED) + "Kbps",
                "JIT:" + status.getInt(TXLiveConstants.NET_STATUS_NET_JITTER),
                "FPS:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_FPS),
                "GOP:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_GOP)+"s",
                "ARA:" + status.getInt(TXLiveConstants.NET_STATUS_AUDIO_BITRATE) + "Kbps",
                "QUE:"+status.getInt(TXLiveConstants.NET_STATUS_CODEC_CACHE)
                        +"|"+status.getInt(TXLiveConstants.NET_STATUS_CACHE_SIZE)
                        +","+status.getInt(TXLiveConstants.NET_STATUS_VIDEO_CACHE_SIZE)
                        +","+status.getInt(TXLiveConstants.NET_STATUS_V_DEC_CACHE_SIZE)
                        +"|"+status.getInt(TXLiveConstants.NET_STATUS_AV_RECV_INTERVAL)
                        +","+status.getInt(TXLiveConstants.NET_STATUS_AV_PLAY_INTERVAL)
                        +","+status.getFloat(TXLiveConstants.NET_STATUS_AUDIO_PLAY_SPEED),
                "DRP:"+status.getInt(TXLiveConstants.NET_STATUS_CODEC_DROP_CNT)+"|"+status.getInt(TXLiveConstants.NET_STATUS_DROP_SIZE),
                "VRA:" + status.getInt(TXLiveConstants.NET_STATUS_VIDEO_BITRATE) + "Kbps",
                "SVR:" + status.getString(TXLiveConstants.NET_STATUS_SERVER_IP),
                "AUDIO:"+status.getString(TXLiveConstants.NET_STATUS_AUDIO_INFO));
        return str;
    }

    /**
     * 根据视频人数更新自己的码率和比例
     *
     * @param VMemNum 当前远端视频人数
     */
    private void updatePushVideoParam(int VMemNum) {
        VMemNum = VMemNum + 1; //总计所有人 +1 代表自己
        TXCLog.i(TAG, "updatePushVideoParam " + VMemNum);
        switch (VMemNum) {
            case 0:
            case 1:
                mAVRoom.setVideoBitrateAndvideoAspect(600, TXCAVRoomConfig.AVROOM_VIDEO_ASPECT_9_16);
                break;
            case 2:
                mAVRoom.setVideoBitrateAndvideoAspect(600, TXCAVRoomConfig.AVROOM_VIDEO_ASPECT_1_1);
                break;
            case 3:
            case 4:
                mAVRoom.setVideoBitrateAndvideoAspect(400, TXCAVRoomConfig.AVROOM_VIDEO_ASPECT_9_16);
                break;
            case 5:
            case 6:
                mAVRoom.setVideoBitrateAndvideoAspect(300, TXCAVRoomConfig.AVROOM_VIDEO_ASPECT_1_1);
                break;
            case 7:
            case 8:
                mAVRoom.setVideoBitrateAndvideoAspect(200, TXCAVRoomConfig.AVROOM_VIDEO_ASPECT_1_1);
                break;
            default:
                mAVRoom.setVideoBitrateAndvideoAspect(200, TXCAVRoomConfig.AVROOM_VIDEO_ASPECT_1_1);
                break;

        }

    }


    private ScrollView findScrollView(long id) {
        View view = isExistLogView(id);
        if (view == null) return null;
        ScrollView scrollView = (ScrollView) view.findViewById(R.id.scrollview);
        return scrollView;
    }

    private TextView findLogEventView(long id) {
        View view = isExistLogView(id);
        if (view == null) return null;
        TextView logViewEvent = (TextView) view.findViewById(R.id.logViewEvent);
        return logViewEvent;
    }

    private TextView findLogStatusView(long id) {
        View view = isExistLogView(id);
        if (view == null) return null;
        TextView logViewStatus = (TextView) view.findViewById(R.id.logViewStatus);
        return logViewStatus;
    }


    /**
     * 实现EVENT VIEW的滚动显示
     */
    public static void scroll2Bottom(final ScrollView scroll, final View inner) {
        if (scroll == null || inner == null) {
            return;
        }
        int offset = inner.getMeasuredHeight() - scroll.getMeasuredHeight();
        if (offset < 0) {
            offset = 0;
        }
        scroll.scrollTo(0, offset);
    }


    private void updateViewLayout() {
        RemoteCloudVideoView view, view1, view2, view3, view4, view5, view6, view7;
        FrameLayout.LayoutParams lp, lp1, lp2, lp3, lp4, lp5, lp6, lp7, lp8, lp9;
        WindowManager wm1 = this.getWindowManager();
        final int width = wm1.getDefaultDisplay().getWidth();
        final int height = wm1.getDefaultDisplay().getHeight();

        if (mRemoteViews == null) return;
        switch (mRemoteViews.size()) {
            case 0:
                TXCLog.i(TAG, "updateViewLayout  0: ");
                lp = new FrameLayout.LayoutParams(width,
                        height);
                mCaptureView.setLayoutParams(lp);
                break;
            case 1:
                if (mCaptureView.getVisibility() == View.VISIBLE) {
                    TXCLog.i(TAG, "updateViewLayout  1: ");
                    lp = new FrameLayout.LayoutParams(width,
                            height / 2);
                    lp.topMargin = height / 2;
                    mCaptureView.setLayoutParams(lp); //自己


                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width,
                            height / 2);
                    lp1.gravity = Gravity.LEFT | Gravity.TOP;
                    if (view1.getVideoView() != null)
                        view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);

                    lp2 = new FrameLayout.LayoutParams(width,
                            height / 2);
                    lp2.gravity = Gravity.TOP;
                    mRemoteViewLayer.setLayoutParams(lp2);//对方
                } else {
                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width,
                            height);
                    lp1.gravity = Gravity.LEFT | Gravity.TOP;
                    if (view1.getVideoView() != null)
                        view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);

                    lp2 = new FrameLayout.LayoutParams(width,
                            height);
                    lp2.gravity = Gravity.TOP;
                    mRemoteViewLayer.setLayoutParams(lp2);//对方
                }
                break;
            case 2:
                if (mCaptureView.getVisibility() == View.VISIBLE) {
                    TXCLog.i(TAG, "updateViewLayout  2: ");
                    lp = new FrameLayout.LayoutParams(width,
                            height / 2);
                    lp.topMargin = height / 2;
                    mCaptureView.setLayoutParams(lp);//自己


                    mRemoteViewLayer.removeAllViews();
                    lp3 = new FrameLayout.LayoutParams(width,
                            height / 2);
                    lp3.gravity = Gravity.TOP;
                    mRemoteViewLayer.setLayoutParams(lp3);//对方

                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width / 2,
                            height / 2);
                    lp1.gravity = Gravity.LEFT;
                    if (view1.getVideoView() != null)
                        view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);
                    mRemoteViewLayer.addView(view1);

                    view2 = mRemoteViews.get(1);
                    lp2 = new FrameLayout.LayoutParams(width / 2,
                            height / 2);
                    lp2.gravity = Gravity.RIGHT;
                    if (view2.getVideoView() != null)
                        view2.getVideoView().setLayoutParams(lp2);
                    view2.setLayoutParams(lp2);
                    mRemoteViewLayer.addView(view2);
                } else {

                    mRemoteViewLayer.removeAllViews();
                    lp3 = new FrameLayout.LayoutParams(width,
                            height);
                    lp3.gravity = Gravity.TOP;
                    mRemoteViewLayer.setLayoutParams(lp3);//对方

                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width,
                            height / 2);
                    lp1.gravity = Gravity.TOP;
                    if (view1.getVideoView() != null)
                        view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);
                    mRemoteViewLayer.addView(view1);

                    view2 = mRemoteViews.get(1);
                    lp2 = new FrameLayout.LayoutParams(width,
                            height / 2);
                    lp2.gravity = Gravity.BOTTOM;
                    if (view2.getVideoView() != null)
                        view2.getVideoView().setLayoutParams(lp2);
                    view2.setLayoutParams(lp2);
                    mRemoteViewLayer.addView(view2);

                }

                break;
            case 3:
                if (mCaptureView.getVisibility() == View.VISIBLE) {

                    TXCLog.i(TAG, "updateViewLayout  3: width " + width + " height " + height + "FrameLayout.LayoutParams.MATCH_PARENT " + FrameLayout.LayoutParams.MATCH_PARENT);
                    lp = new FrameLayout.LayoutParams(width / 2,
                            height / 2);
                    lp.gravity = Gravity.RIGHT;
                    lp.topMargin = height / 2;
                    mCaptureView.setLayoutParams(lp);//自己


                    mRemoteViewLayer.removeAllViews();
                    lp4 = new FrameLayout.LayoutParams(width,
                            height);
                    lp4.gravity = Gravity.TOP;
                    mRemoteViewLayer.setLayoutParams(lp4);//对方

                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width / 2,
                            height / 2);
                    lp1.gravity = Gravity.LEFT | Gravity.TOP;
                    if (view1.getVideoView() != null)
                        view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);
                    mRemoteViewLayer.addView(view1);

                    view2 = mRemoteViews.get(1);
                    lp2 = new FrameLayout.LayoutParams(width / 2,
                            height / 2);
                    lp2.gravity = Gravity.TOP | Gravity.RIGHT;
                    if (view2.getVideoView() != null)
                        view2.getVideoView().setLayoutParams(lp2);
                    view2.setLayoutParams(lp2);
                    mRemoteViewLayer.addView(view2);

                    view3 = mRemoteViews.get(2);
                    lp3 = new FrameLayout.LayoutParams(width / 2,
                            height / 2);
                    lp3.gravity = Gravity.LEFT | Gravity.BOTTOM;
                    if (view3.getVideoView() != null)
                        view3.getVideoView().setLayoutParams(lp3);
                    view3.setLayoutParams(lp3);
                    mRemoteViewLayer.addView(view3);
                } else {
                    mRemoteViewLayer.removeAllViews();
                    lp4 = new FrameLayout.LayoutParams(width,
                            height);
                    mRemoteViewLayer.setLayoutParams(lp4);//对方

                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width / 2,
                            height / 2);
                    lp1.gravity = Gravity.LEFT | Gravity.TOP;
                    if (view1.getVideoView() != null)
                        view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);
                    mRemoteViewLayer.addView(view1);

                    view2 = mRemoteViews.get(1);
                    lp2 = new FrameLayout.LayoutParams(width / 2,
                            height / 2);
                    lp2.gravity = Gravity.TOP | Gravity.RIGHT;
                    if (view2.getVideoView() != null)
                        view2.getVideoView().setLayoutParams(lp2);
                    view2.setLayoutParams(lp2);
                    mRemoteViewLayer.addView(view2);

                    view3 = mRemoteViews.get(2);
                    lp3 = new FrameLayout.LayoutParams(width,
                            height / 2);
                    lp3.gravity = Gravity.BOTTOM;
                    if (view3.getVideoView() != null)
                        view3.getVideoView().setLayoutParams(lp3);
                    view3.setLayoutParams(lp3);
                    mRemoteViewLayer.addView(view3);
                }

                break;
            case 4:
                if (mCaptureView.getVisibility() == View.VISIBLE) {
                    TXCLog.i(TAG, "updateViewLayout  4: ");
                    lp = new FrameLayout.LayoutParams(width,
                            height / 3);
                    lp.topMargin = height * 2 / 3;
                    mCaptureView.setLayoutParams(lp);//自己

                    mRemoteViewLayer.removeAllViews();
                    lp5 = new FrameLayout.LayoutParams(width,
                            (height * 2 / 3));
                    lp5.gravity = Gravity.TOP;
                    mRemoteViewLayer.setLayoutParams(lp5);//对方

                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp1.gravity = Gravity.LEFT | Gravity.TOP;
                    if (view1.getVideoView() != null)
                        view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);
                    mRemoteViewLayer.addView(view1);

                    view2 = mRemoteViews.get(1);
                    lp2 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp2.gravity = Gravity.RIGHT | Gravity.TOP;
                    if (view2.getVideoView() != null)
                        view2.getVideoView().setLayoutParams(lp2);
                    view2.setLayoutParams(lp2);
                    mRemoteViewLayer.addView(view2);

                    view3 = mRemoteViews.get(2);
                    lp3 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp3.gravity = Gravity.LEFT | Gravity.BOTTOM;
                    if (view3.getVideoView() != null)
                        view3.getVideoView().setLayoutParams(lp3);
                    view3.setLayoutParams(lp3);
                    mRemoteViewLayer.addView(view3);


                    view4 = mRemoteViews.get(3);
                    lp4 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp4.gravity = Gravity.RIGHT | Gravity.BOTTOM;
                    if (view4.getVideoView() != null)
                        view4.getVideoView().setLayoutParams(lp4);
                    view4.setLayoutParams(lp4);
                    mRemoteViewLayer.addView(view4);
                } else {
                    mRemoteViewLayer.removeAllViews();
                    lp5 = new FrameLayout.LayoutParams(width,
                            height);
                    mRemoteViewLayer.setLayoutParams(lp5);//对方

                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width / 2,
                            height / 2);
                    lp1.gravity = Gravity.LEFT | Gravity.TOP;
                    if (view1.getVideoView() != null)
                        view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);
                    mRemoteViewLayer.addView(view1);

                    view2 = mRemoteViews.get(1);
                    lp2 = new FrameLayout.LayoutParams(width / 2,
                            height / 2);
                    lp2.gravity = Gravity.RIGHT | Gravity.TOP;
                    if (view2.getVideoView() != null)
                        view2.getVideoView().setLayoutParams(lp2);
                    view2.setLayoutParams(lp2);
                    mRemoteViewLayer.addView(view2);

                    view3 = mRemoteViews.get(2);
                    lp3 = new FrameLayout.LayoutParams(width / 2,
                            height / 2);
                    lp3.gravity = Gravity.LEFT | Gravity.BOTTOM;
                    if (view3.getVideoView() != null)
                        view3.getVideoView().setLayoutParams(lp3);
                    view3.setLayoutParams(lp3);
                    mRemoteViewLayer.addView(view3);


                    view4 = mRemoteViews.get(3);
                    lp4 = new FrameLayout.LayoutParams(width / 2,
                            height / 2);
                    lp4.gravity = Gravity.RIGHT | Gravity.BOTTOM;
                    if (view4.getVideoView() != null)
                        view4.getVideoView().setLayoutParams(lp4);
                    view4.setLayoutParams(lp4);
                    mRemoteViewLayer.addView(view4);
                }
                break;
            case 5:
                if (mCaptureView.getVisibility() == View.VISIBLE) {
                    TXCLog.i(TAG, "updateViewLayout  5: height " + height + " width " + width);
                    lp = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp.topMargin = height * 2 / 3;
                    lp.gravity = Gravity.RIGHT;
                    mCaptureView.setLayoutParams(lp);//自己

                    mRemoteViewLayer.removeAllViews();
                    lp6 = new FrameLayout.LayoutParams(width,
                            height);
                    lp6.gravity = Gravity.TOP;
                    mRemoteViewLayer.setLayoutParams(lp6);//对方

                    //左上
                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp1.gravity = Gravity.LEFT | Gravity.TOP;
                    if (view1.getVideoView() != null)
                        view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);
                    mRemoteViewLayer.addView(view1);

                    //右上
                    view2 = mRemoteViews.get(1);
                    lp2 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp2.gravity = Gravity.RIGHT | Gravity.TOP;
                    if (view2.getVideoView() != null)
                        view2.getVideoView().setLayoutParams(lp2);
                    view2.setLayoutParams(lp2);
                    mRemoteViewLayer.addView(view2);

                    //左中
                    view3 = mRemoteViews.get(2);
                    lp3 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
//                lp3.topMargin = height / 3;
//                lp3.leftMargin = 0;
                    lp3.gravity = Gravity.CENTER | Gravity.LEFT;
                    if (view3.getVideoView() != null)
                        view3.getVideoView().setLayoutParams(lp3);
                    view3.setLayoutParams(lp3);
                    mRemoteViewLayer.addView(view3);

                    //右中
                    view4 = mRemoteViews.get(3);
                    lp4 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp4.gravity = Gravity.CENTER | Gravity.RIGHT;
                    if (view4.getVideoView() != null)
                        view4.getVideoView().setLayoutParams(lp4);
                    view4.setLayoutParams(lp4);
                    mRemoteViewLayer.addView(view4);


                    //左下
                    view5 = mRemoteViews.get(4);
                    lp5 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
//                lp5.topMargin = height * 2 / 3;
                    lp5.gravity = Gravity.BOTTOM | Gravity.LEFT;
                    if (view5.getVideoView() != null)
                        view5.getVideoView().setLayoutParams(lp5);
                    view5.setLayoutParams(lp5);
                    mRemoteViewLayer.addView(view5);
                } else {
                    mRemoteViewLayer.removeAllViews();
                    lp6 = new FrameLayout.LayoutParams(width,
                            height);
                    lp6.gravity = Gravity.TOP;
                    mRemoteViewLayer.setLayoutParams(lp6);//对方

                    //左上
                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp1.gravity = Gravity.LEFT | Gravity.TOP;
                    if (view1.getVideoView() != null)
                        view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);
                    mRemoteViewLayer.addView(view1);

                    //右上
                    view2 = mRemoteViews.get(1);
                    lp2 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp2.gravity = Gravity.RIGHT | Gravity.TOP;
                    if (view2.getVideoView() != null)
                        view2.getVideoView().setLayoutParams(lp2);
                    view2.setLayoutParams(lp2);
                    mRemoteViewLayer.addView(view2);

                    //左中
                    view3 = mRemoteViews.get(2);
                    lp3 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp3.gravity = Gravity.CENTER | Gravity.LEFT;
                    if (view3.getVideoView() != null)
                        view3.getVideoView().setLayoutParams(lp3);
                    view3.setLayoutParams(lp3);
                    mRemoteViewLayer.addView(view3);

                    //右中
                    view4 = mRemoteViews.get(3);
                    lp4 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp4.gravity = Gravity.CENTER | Gravity.RIGHT;
                    if (view4.getVideoView() != null)
                        view4.getVideoView().setLayoutParams(lp4);
                    view4.setLayoutParams(lp4);
                    mRemoteViewLayer.addView(view4);


                    //左下
                    view5 = mRemoteViews.get(4);
                    lp5 = new FrameLayout.LayoutParams(width,
                            height / 3);
                    lp5.gravity = Gravity.BOTTOM;
                    if (view5.getVideoView() != null)
                        view5.getVideoView().setLayoutParams(lp5);
                    view5.setLayoutParams(lp5);
                    mRemoteViewLayer.addView(view5);
                }
                break;
            case 6:
                if (mCaptureView.getVisibility() == View.VISIBLE) {
                    TXCLog.i(TAG, "updateViewLayout  6: ");
                    lp = new FrameLayout.LayoutParams(width,
                            height / 4);
                    lp.topMargin = height * 3 / 4;
                    mCaptureView.setLayoutParams(lp);//自己

                    mRemoteViewLayer.removeAllViews();
                    lp9 = new FrameLayout.LayoutParams(width,
                            height * 3 / 4);
                    lp9.gravity = Gravity.TOP;
                    mRemoteViewLayer.setLayoutParams(lp9);//对方

                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp1.gravity = Gravity.TOP | Gravity.LEFT;
                    if (view1.getVideoView() != null)
                        view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);
                    mRemoteViewLayer.addView(view1);

                    view2 = mRemoteViews.get(1);
                    lp2 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp2.gravity = Gravity.RIGHT | Gravity.TOP;
                    if (view2.getVideoView() != null)
                        view2.getVideoView().setLayoutParams(lp2);
                    view2.setLayoutParams(lp2);
                    mRemoteViewLayer.addView(view2);

                    view3 = mRemoteViews.get(2);
                    lp3 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp3.gravity = Gravity.LEFT | Gravity.CENTER;
                    if (view3.getVideoView() != null)
                        view3.getVideoView().setLayoutParams(lp3);
                    view3.setLayoutParams(lp3);
                    mRemoteViewLayer.addView(view3);


                    view4 = mRemoteViews.get(3);
                    lp4 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp4.gravity = Gravity.RIGHT | Gravity.CENTER;
                    if (view4.getVideoView() != null)
                        view4.getVideoView().setLayoutParams(lp4);
                    view4.setLayoutParams(lp4);
                    mRemoteViewLayer.addView(view4);


                    view5 = mRemoteViews.get(4);
                    lp5 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp5.gravity = Gravity.LEFT | Gravity.BOTTOM;
                    if (view5.getVideoView() != null)
                        view5.getVideoView().setLayoutParams(lp5);
                    view5.setLayoutParams(lp5);
                    mRemoteViewLayer.addView(view5);

                    view6 = mRemoteViews.get(5);
                    lp6 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
//                lp6.topMargin = height / 2;
                    lp6.gravity = Gravity.RIGHT | Gravity.BOTTOM;
                    if (view6.getVideoView() != null)
                        view6.getVideoView().setLayoutParams(lp6);
                    view6.setLayoutParams(lp6);
                    mRemoteViewLayer.addView(view6);
                } else {
                    mRemoteViewLayer.removeAllViews();
                    lp9 = new FrameLayout.LayoutParams(width,
                            height);
                    lp9.gravity = Gravity.TOP;
                    mRemoteViewLayer.setLayoutParams(lp9);//对方

                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp1.gravity = Gravity.TOP | Gravity.LEFT;
                    if (view1.getVideoView() != null)
                        view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);
                    mRemoteViewLayer.addView(view1);

                    view2 = mRemoteViews.get(1);
                    lp2 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp2.gravity = Gravity.RIGHT | Gravity.TOP;
                    if (view2.getVideoView() != null)
                        view2.getVideoView().setLayoutParams(lp2);
                    view2.setLayoutParams(lp2);
                    mRemoteViewLayer.addView(view2);

                    view3 = mRemoteViews.get(2);
                    lp3 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp3.gravity = Gravity.LEFT | Gravity.CENTER;
                    if (view3.getVideoView() != null)
                        view3.getVideoView().setLayoutParams(lp3);
                    view3.setLayoutParams(lp3);
                    mRemoteViewLayer.addView(view3);


                    view4 = mRemoteViews.get(3);
                    lp4 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp4.gravity = Gravity.RIGHT | Gravity.CENTER;
                    if (view4.getVideoView() != null)
                        view4.getVideoView().setLayoutParams(lp4);
                    view4.setLayoutParams(lp4);
                    mRemoteViewLayer.addView(view4);


                    view5 = mRemoteViews.get(4);
                    lp5 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp5.gravity = Gravity.LEFT | Gravity.BOTTOM;
                    if (view5.getVideoView() != null)
                        view5.getVideoView().setLayoutParams(lp5);
                    view5.setLayoutParams(lp5);
                    mRemoteViewLayer.addView(view5);

                    view6 = mRemoteViews.get(5);
                    lp6 = new FrameLayout.LayoutParams(width / 2,
                            height / 3);
                    lp6.gravity = Gravity.RIGHT | Gravity.BOTTOM;
                    if (view6.getVideoView() != null)
                        view6.getVideoView().setLayoutParams(lp6);
                    view6.setLayoutParams(lp6);
                    mRemoteViewLayer.addView(view6);
                }
                break;
            case 7:
                if (mCaptureView.getVisibility() == View.VISIBLE) {

                    TXCLog.i(TAG, "updateViewLayout  7: ");
                    lp = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp.gravity = Gravity.RIGHT;
                    lp.topMargin = height * 3 / 4;
                    mCaptureView.setLayoutParams(lp);//自己

                    mRemoteViewLayer.removeAllViews();
                    lp9 = new FrameLayout.LayoutParams(width,
                            height);
                    lp9.gravity = Gravity.TOP;
                    mRemoteViewLayer.setLayoutParams(lp9);//对方

                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp1.gravity = Gravity.TOP | Gravity.LEFT;
                    view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);
                    mRemoteViewLayer.addView(view1);

                    view2 = mRemoteViews.get(1);
                    lp2 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp2.gravity = Gravity.RIGHT | Gravity.TOP;
                    view2.getVideoView().setLayoutParams(lp2);
                    view2.setLayoutParams(lp2);
                    mRemoteViewLayer.addView(view2);

                    view3 = mRemoteViews.get(2);
                    lp3 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp3.gravity = Gravity.LEFT;
                    lp3.topMargin = height / 4;
//                lp3.leftMargin = 0;
//                view3.getVideoView().setLayoutParams(lp3);
                    view3.setLayoutParams(lp3);
                    mRemoteViewLayer.addView(view3);


                    view4 = mRemoteViews.get(3);
                    lp4 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp4.gravity = Gravity.RIGHT;
                    lp4.topMargin = height / 4;
//                view4.getVideoView().setLayoutParams(lp4);
                    view4.setLayoutParams(lp4);
                    mRemoteViewLayer.addView(view4);


                    view5 = mRemoteViews.get(4);
                    lp5 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp5.gravity = Gravity.LEFT;
                    lp5.topMargin = height / 2;
//                view5.getVideoView().setLayoutParams(lp5);
                    view5.setLayoutParams(lp5);
                    mRemoteViewLayer.addView(view5);


                    view6 = mRemoteViews.get(5);
                    lp6 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp6.gravity = Gravity.RIGHT;
                    lp6.topMargin = height / 2;
//                view6.getVideoView().setLayoutParams(lp6);
                    view6.setLayoutParams(lp6);
                    mRemoteViewLayer.addView(view6);


                    view7 = mRemoteViews.get(6);
                    lp7 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp7.gravity = Gravity.LEFT;
                    lp7.topMargin = height * 3 / 4;
//                if (view7.getVideoView() != null)
//                    view7.getVideoView().setLayoutParams(lp7);
                    view7.setLayoutParams(lp7);
                    mRemoteViewLayer.addView(view7);
                } else {
                    mRemoteViewLayer.removeAllViews();
                    lp9 = new FrameLayout.LayoutParams(width,
                            height);
                    lp9.gravity = Gravity.TOP;
                    mRemoteViewLayer.setLayoutParams(lp9);//对方

                    view1 = mRemoteViews.get(0);
                    lp1 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp1.gravity = Gravity.TOP | Gravity.LEFT;
                    view1.getVideoView().setLayoutParams(lp1);
                    view1.setLayoutParams(lp1);
                    mRemoteViewLayer.addView(view1);

                    view2 = mRemoteViews.get(1);
                    lp2 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp2.gravity = Gravity.RIGHT | Gravity.TOP;
                    view2.getVideoView().setLayoutParams(lp2);
                    view2.setLayoutParams(lp2);
                    mRemoteViewLayer.addView(view2);

                    view3 = mRemoteViews.get(2);
                    lp3 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp3.gravity = Gravity.LEFT;
                    lp3.topMargin = height / 4;
//                lp3.leftMargin = 0;
//                view3.getVideoView().setLayoutParams(lp3);
                    view3.setLayoutParams(lp3);
                    mRemoteViewLayer.addView(view3);


                    view4 = mRemoteViews.get(3);
                    lp4 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp4.gravity = Gravity.RIGHT;
                    lp4.topMargin = height / 4;
//                view4.getVideoView().setLayoutParams(lp4);
                    view4.setLayoutParams(lp4);
                    mRemoteViewLayer.addView(view4);


                    view5 = mRemoteViews.get(4);
                    lp5 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp5.gravity = Gravity.LEFT;
                    lp5.topMargin = height / 2;
//                view5.getVideoView().setLayoutParams(lp5);
                    view5.setLayoutParams(lp5);
                    mRemoteViewLayer.addView(view5);


                    view6 = mRemoteViews.get(5);
                    lp6 = new FrameLayout.LayoutParams(width / 2,
                            height / 4);
                    lp6.gravity = Gravity.RIGHT;
                    lp6.topMargin = height / 2;
//                view6.getVideoView().setLayoutParams(lp6);
                    view6.setLayoutParams(lp6);
                    mRemoteViewLayer.addView(view6);


                    view7 = mRemoteViews.get(6);
                    lp7 = new FrameLayout.LayoutParams(width,
                            height / 4);
                    lp7.gravity = Gravity.LEFT;
                    lp7.topMargin = height * 3 / 4;
//                if (view7.getVideoView() != null)
//                    view7.getVideoView().setLayoutParams(lp7);
                    view7.setLayoutParams(lp7);
                    mRemoteViewLayer.addView(view7);
                }
            case 8:
            case 9:
            case 10:
                break;

        }


    }

}

package com.tencent.liteav.demo.rtcroom.ui.multi_room.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.tencent.liteav.demo.BuildConfig;
import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.common.widget.BeautySettingPannel;
import com.tencent.liteav.demo.roomutil.misc.AndroidPermissions;
import com.tencent.liteav.demo.roomutil.commondef.PusherInfo;
import com.tencent.liteav.demo.rtcroom.RTCRoom;
import com.tencent.liteav.demo.roomutil.commondef.RoomInfo;
import com.tencent.liteav.demo.rtcroom.IRTCRoomListener;
import com.tencent.liteav.demo.rtcroom.ui.multi_room.RTCMultiRoomActivityInterface;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.util.ArrayList;
import java.util.List;

public class RTCMultiRoomChatFragment extends Fragment implements IRTCRoomListener {
    private static final String TAG = RTCMultiRoomChatFragment.class.getSimpleName();

    private Activity mActivity;
    private RTCMultiRoomActivityInterface myInterface;

    private List<String> members = new ArrayList<>();

    private List<RoomVideoView> mVideoViewsVector = new ArrayList<>();
    private String userID;
    private String userName;
    private RoomInfo roomInfo;
    private boolean mPusherMute = false;

    private class RoomVideoView{
        TXCloudVideoView view;
        TextView titleView;
        boolean isUsed;
        String userID;
        String name = "";

        public RoomVideoView(TXCloudVideoView view, TextView titleView, String userID) {
            this.view = view;
            this.titleView = titleView;
            titleView.setText("");
            view.setVisibility(View.GONE);
            this.isUsed = false;
            this.userID = userID;
        }

        private void setUsed(boolean set){
            view.setVisibility(set ? View.VISIBLE : View.GONE);
            titleView.setVisibility(set ? View.VISIBLE : View.GONE);
            titleView.setText(set ? name : "");
            this.isUsed = set;
        }

    }

    public synchronized RoomVideoView applyVideoView(String id, String name){

        if (id == null || this.userID == null) {
            Log.w(TAG, "applyVideoView: member/id is null");
            return null;
        }

        for (RoomVideoView videoView : mVideoViewsVector) {
            if (!videoView.isUsed) {

                videoView.name = name;
                videoView.setUsed(true);
                videoView.userID = id;
                return videoView;
            }else {
                if (videoView.userID != null
                        && videoView.userID.equals(id)){

                    videoView.name = name;
                    videoView.setUsed(true);
                    return videoView;
                }
            }
        }
        return null;
    }

    public synchronized void recycleView(String id){
        Log.i(TAG, "recycleView() called with: UserID = [" + id + "]");
        for (RoomVideoView V : mVideoViewsVector) {
            if (V.userID != null
                    && V.userID.equals(id)){
                V.setUsed(false);
                V.userID = null;
            }
        }
    }

    public synchronized void recycleView(){
        for (RoomVideoView V : mVideoViewsVector) {
            Log.i(TAG, "recycleView() for remove member userID "+ V.userID);
            V.setUsed(false);
            V.userID = null;
        }
    }

    public static RTCMultiRoomChatFragment newInstance(RoomInfo config, String userID, boolean createRoom) {
        RTCMultiRoomChatFragment fragment = new RTCMultiRoomChatFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable("roomInfo", config);
        bundle.putString("userID", userID);
        bundle.putBoolean("createRoom", createRoom);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rtc_multi_room_chat, container, false);
        (view.findViewById(R.id.rtmproom_log_switcher_btn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchLog();
            }
        });

        (view.findViewById(R.id.rtmproom_camera_switcher_btn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myInterface != null)
                    myInterface.getRTCRoom().switchCamera();
            }
        });

        view.findViewById(R.id.rtmproom_mute_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPusherMute = !mPusherMute;
                myInterface.getRTCRoom().setMute(mPusherMute);
                v.setBackgroundResource(mPusherMute ? R.drawable.mic_disable : R.drawable.mic_normal);
            }
        });

        view.findViewById(R.id.rtmproom_beauty_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableOneKeyBeauty = !enableOneKeyBeauty;
                oneKeyBeauty(enableOneKeyBeauty);
                v.setBackgroundResource(enableOneKeyBeauty ? R.drawable.beauty : R.drawable.beauty_dis);
            }
        });

        return view;
    }

    private int showVideoViewLog = 0;
    private void switchLog(){
        showVideoViewLog++;
        showVideoViewLog = (showVideoViewLog % 3);
        switch (showVideoViewLog) {
            case 0: {
                for (RoomVideoView videoView : mVideoViewsVector) {
                    if (videoView.isUsed) {
                        videoView.view.showLog(false);
                    }
                }
                if (myInterface != null)
                    myInterface.showGlobalLog(false);
                break;
            }

            case 1:{
                for (RoomVideoView videoView : mVideoViewsVector) {
                    if (videoView.isUsed) {
                        videoView.view.showLog(false);
                    }
                }
                if (myInterface != null)
                    myInterface.showGlobalLog(true);
                break;
            }

            case 2:{
                for (RoomVideoView videoView : mVideoViewsVector) {
                    if (videoView.isUsed) {
                        videoView.view.showLog(true);
                    }
                }
                if (myInterface != null)
                    myInterface.showGlobalLog(false);
                break;
            }
        }
    }

    private void initViews() {
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        TXCloudVideoView views[] = new TXCloudVideoView[6];
        views[0] = ((TXCloudVideoView) mActivity.findViewById(R.id.rtmproom_video_0));
        views[1] = ((TXCloudVideoView) mActivity.findViewById(R.id.rtmproom_video_1));
        views[2] = ((TXCloudVideoView) mActivity.findViewById(R.id.rtmproom_video_2));
        views[3] = ((TXCloudVideoView) mActivity.findViewById(R.id.rtmproom_video_3));

        TextView nameViews[] = new TextView[6];
        nameViews[0] = ((TextView) mActivity.findViewById(R.id.rtmproom_video_name_0));
        nameViews[1] = ((TextView) mActivity.findViewById(R.id.rtmproom_video_name_1));
        nameViews[2] = ((TextView) mActivity.findViewById(R.id.rtmproom_video_name_2));
        nameViews[3] = ((TextView) mActivity.findViewById(R.id.rtmproom_video_name_3));

        for (int i = 0; i < 4; i++) {
            mVideoViewsVector.add(new RoomVideoView(views[i], nameViews[i], null));
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onActivityCreated() called ");
        super.onActivityCreated(savedInstanceState);

        initViews();
        Bundle bundle = getArguments();
        this.roomInfo = bundle.getParcelable("roomInfo");
        this.userID = bundle.getString("userID");
        this.userName = myInterface.getSelfUserName();
        boolean createRoom = bundle.getBoolean("createRoom");

        if (this.userID == null || ( !createRoom && roomInfo == null)) {
            return;
        }

        myInterface.setTitle(roomInfo.roomName);

        RoomVideoView videoView = applyVideoView(this.userID, "我("+userName+")");
        if (videoView == null) {
            myInterface.printGlobalLog("申请 UserID {%s} 返回view 为空", this.userID);
            return;
        }

        myInterface.getRTCRoom().startLocalPreview(videoView.view);
        myInterface.getRTCRoom().setPauseImage(BitmapFactory.decodeResource(getResources(), R.drawable.pause_publish));
        myInterface.getRTCRoom().setBitrateRange(200, 400);
        myInterface.getRTCRoom().setVideoRatio(RTCRoom.RTCROOM_VIDEO_RATIO_3_4);
        myInterface.getRTCRoom().setHDAudio(true);
        myInterface.getRTCRoom().setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);

        if (createRoom){
            myInterface.getRTCRoom().createRoom(roomInfo.roomName, new RTCRoom.CreateRoomCallback() {
                        @Override
                        public void onSuccess(String roomId) {
                            roomInfo.roomID = roomId;
                        }

                        @Override
                        public void onError(int errCode, String e) {
                            errorGoBack("创建会话错误", errCode, e);
                        }
                    });
        }
        else {
            myInterface.getRTCRoom().enterRoom(this.roomInfo.roomID, new RTCRoom.EnterRoomCallback() {
                        @Override
                        public void onError(int errCode, String errInfo) {
                            errorGoBack("进入会话错误", errCode, errInfo);
                        }

                        @Override
                        public void onSuccess() {
                            RTCMultiRoomChatFragment.this.roomInfo = roomInfo;
                            RTCMultiRoomChatFragment.this.members.add(userID);
                        }
                    });
        }
    }

    private void errorGoBack(String title, int errCode, String errInfo){
        myInterface.getRTCRoom().exitRoom(null);

        new AndroidPermissions.HintDialog.Builder(mActivity)
                .setTittle(title)
                .setContent(errInfo + "[" + errCode + "]" )
                .setButtonText("确定")
                .setDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        backStack();
                    }
                }).show();
    }
    @Override
    public void onDestroyView() {
        mVideoViewsVector.clear();
        myInterface.getRTCRoom().stopLocalPreview();
        super.onDestroyView();
    }

    @Override
    public void onAttach(Context context) {
        if (BuildConfig.DEBUG)
            Log.i(TAG, "onAttach() called ");

        super.onAttach(context);

        myInterface = ((RTCMultiRoomActivityInterface) context);
        mActivity = ((Activity) context);
    }

    @Override
    public void onAttach(Activity activity) {
        Log.i(TAG, "onAttach() called with: activity = [" + activity + "]");

        myInterface = ((RTCMultiRoomActivityInterface) activity);
        mActivity = ((Activity) activity);
        super.onAttach(activity);

    }

    @Override
    public void onDetach() {
        if (BuildConfig.DEBUG)
            Log.i(TAG, "onDetach() called");

        myInterface = null;
        mActivity = null;
        super.onDetach();
    }

    @Override
    public void onResume() {
        if (BuildConfig.DEBUG)
            Log.i(TAG, "onResume() called");
        super.onResume();

        myInterface.getRTCRoom().switchToForeground();
    }

    @Override
    public void onPause() {
        if (BuildConfig.DEBUG)
            Log.i(TAG, "onPause() called");

        super.onPause();

        myInterface.getRTCRoom().switchToBackground();
    }

    @Override
    public void onDestroy() {
        recycleView();
        super.onDestroy();
    }

    /**
     * call by activity
     */
    public void onBackPressed() {
        if (myInterface != null) {
            myInterface.getRTCRoom().exitRoom(new RTCRoom.ExitRoomCallback() {
                @Override
                public void onSuccess() {
                    Log.i(TAG, "exitRoom Success");
                }

                @Override
                public void onError(int errCode, String e) {
                    Log.e(TAG, "exitRoom failed, errorCode = " + errCode + " errMessage = " + e);
                }
            });
        }

        recycleView();
        backStack();
    }


    @Override
    public void onPusherJoin(PusherInfo member) {

        if (member == null || member.userID == null) {
            Log.w(TAG, "onPusherJoin: member or memeber id is null");
            return;
        }

        RoomVideoView V = applyVideoView(member.userID, member.userName == null ? member.userID : member.userName);
        if (V == null) return;
        myInterface.getRTCRoom().addRemoteView(V.view, member); //开启远端视频渲染

    }

    @Override
    public void onPusherQuit(PusherInfo member) {
        Log.i(TAG, "onPusherQuit() called with: member =  "+ member );

        myInterface.getRTCRoom().delRemoteView(member);//关闭远端视频渲染
        recycleView(member.userID);
    }

    @Override
    public void onRoomClosed(String roomId) {
        boolean createRoom = getArguments().getBoolean("createRoom");
        if (createRoom == false) {
            new AndroidPermissions.HintDialog.Builder(mActivity)
                    .setTittle("系统消息")
                    .setContent(String.format("会话【%s】解散了", roomInfo != null ? roomInfo.roomName : "null"))
                    .setButtonText("返回")
                    .setDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            onBackPressed();
                        }
                    }).show();
        }
    }

    @Override
    public void onDebugLog(String line) {
        //do nothing
    }

    @Override
    public void onGetPusherList(List<PusherInfo> pusherInfoList) {
       //do nothing
    }

    @Override
    public void onRecvRoomTextMsg(String roomid, String userid, String userName, String userAvatar, String msg) {
        //do nothing
    }

    @Override
    public void onError(final int errorCode, final String errorMessage) {
        errorGoBack("会话错误", errorCode, errorMessage);
    }

    private void backStack(){
        if (mActivity != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mActivity != null) {
                        FragmentManager fragmentManager = mActivity.getFragmentManager();
                        fragmentManager.popBackStack();
                        fragmentManager.beginTransaction().commit();
                    }
                }
            });
        }
    }

    private int mBeautyStyle = TXLiveConstants.BEAUTY_STYLE_SMOOTH;
    private int mBeautyLevel = 5;
    private int mWhiteningLevel = 5;
    private int mRuddyLevel = 5;
    private boolean enableOneKeyBeauty = true;

    private void oneKeyBeauty(boolean enable){
        if (enable) {
            myInterface.getRTCRoom().setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
        }else {
            myInterface.getRTCRoom().setBeautyFilter(mBeautyStyle, 0, 0, 0);
        }
    }


/*

    @Override
    public void onBeautyParamsChange(BeautySettingPannel.BeautyParams params, int key) {
        switch (key) {
            case BeautySettingPannel.BEAUTYPARAM_EXPOSURE:
                if (myInterface != null) {
                    myInterface.getRTCRoom().setExposureCompensation(params.mExposure);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY:
                mBeautyLevel = params.mBeautyLevel;
                if (myInterface != null) {
                    myInterface.getRTCRoom().setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_WHITE:
                mWhiteningLevel = params.mWhiteLevel;
                if (myInterface != null) {
                    myInterface.getRTCRoom().setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BIG_EYE:
                if (myInterface != null) {
                    myInterface.getRTCRoom().setEyeScaleLevel(params.mBigEyeLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACE_LIFT:
                if (myInterface != null) {
                    myInterface.getRTCRoom().setFaceSlimLevel(params.mFaceSlimLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FILTER:
                if (myInterface != null) {
                    myInterface.getRTCRoom().setFilter(params.mFilterBmp);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_GREEN:
                if (myInterface != null) {
                    myInterface.getRTCRoom().setGreenScreenFile(params.mGreenFile);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_MOTION_TMPL:
                if (myInterface != null) {
                    myInterface.getRTCRoom().setMotionTmpl(params.mMotionTmplPath);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_RUDDY:
                mRuddyLevel = params.mRuddyLevel;
                if (myInterface != null) {
                    myInterface.getRTCRoom().setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY_STYLE:
                mBeautyStyle = params.mBeautyStyle;
                if (myInterface != null) {
                    myInterface.getRTCRoom().setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACEV:
                if (myInterface != null) {
                    myInterface.getRTCRoom().setFaceVLevel(params.mFaceVLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACESHORT:
                if (myInterface != null) {
                    myInterface.getRTCRoom().setFaceShortLevel(params.mFaceShortLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_CHINSLIME:
                if (myInterface != null) {
                    myInterface.getRTCRoom().setChinLevel(params.mChinSlimLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_NOSESCALE:
                if (myInterface != null) {
                    myInterface.getRTCRoom().setNoseSlimLevel(params.mNoseScaleLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FILTER_MIX_LEVEL:
                if (myInterface != null) {
                    myInterface.getRTCRoom().setSpecialRatio(params.mFilterMixLevel / 10.f);
                }
                break;
        }
    }

*/

}

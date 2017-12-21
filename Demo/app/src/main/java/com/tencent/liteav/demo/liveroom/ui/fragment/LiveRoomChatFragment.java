package com.tencent.liteav.demo.liveroom.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.tencent.liteav.demo.BuildConfig;
import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.common.widget.BeautySettingPannel;
import com.tencent.liteav.demo.liveroom.ILiveRoomListener;
import com.tencent.liteav.demo.roomutil.commondef.RoomInfo;
import com.tencent.liteav.demo.liveroom.LiveRoom;
import com.tencent.liteav.demo.roomutil.commondef.TextChatMsg;
import com.tencent.liteav.demo.liveroom.ui.LiveRoomActivityInterface;
import com.tencent.liteav.demo.roomutil.widget.ChatMessageAdapter;
import com.tencent.liteav.demo.roomutil.misc.HintDialog;
import com.tencent.liteav.demo.roomutil.widget.TextMsgInputDialog;
import com.tencent.liteav.demo.roomutil.widget.SwipeAnimationController;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LiveRoomChatFragment extends Fragment implements BeautySettingPannel.IOnBeautyParamsChangeListener,ILiveRoomListener {

    private static final String TAG = LiveRoomChatFragment.class.getSimpleName();

    private Activity mActivity;
    private LiveRoomActivityInterface myInterface;

    private List<String> members = new ArrayList<>();

    private List<RoomVideoView> mVideoViewsVector = new ArrayList<>();
    private String userID;
    private String userName;
    private RoomInfo roomInfo;
    private ListView chatListView;
    private EditText chatEditText;
    private LinearLayout mControllerLayout;
    private BeautySettingPannel mBeautyPannelView;
    private ArrayList<TextChatMsg> textChatMsgList;
    private ChatMessageAdapter chatMessageAdapter;
    private TextMsgInputDialog mTextMsgInputDialog;
    private SwipeAnimationController mSwipeAnimationController;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");

    private int mBeautyLevel = 5;
    private int mWhiteningLevel = 3;
    private int mRuddyLevel = 2;
    private int mBeautyStyle = TXLiveConstants.BEAUTY_STYLE_SMOOTH;

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
            titleView.setVisibility(View.GONE);
            titleView.setText(set ? name : "");
            this.isUsed = set;
        }

    }

    public synchronized RoomVideoView applyVideoView(String id, String name){

        Log.i(TAG, "applyVideoView() called with: userID = [" + id + "]");

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

    public static LiveRoomChatFragment newInstance(RoomInfo config, String userID, boolean createRoom) {
        LiveRoomChatFragment fragment = new LiveRoomChatFragment();
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
        View view = inflater.inflate(R.layout.fragment_live_room_chat, container, false);
        //切换摄像头
        (view.findViewById(R.id.rtmproom_camera_switcher_btn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myInterface != null)
                    myInterface.getLiveRoom().switchCamera();
            }
        });

        view.findViewById(R.id.rtmproom_mute_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPusherMute = !mPusherMute;
                myInterface.getLiveRoom().setMute(mPusherMute);
                v.setBackgroundResource(mPusherMute ? R.drawable.mic_disable : R.drawable.mic_normal);
            }
        });

        //美颜p图部分
        mBeautyPannelView = (BeautySettingPannel) view.findViewById(R.id.layoutFaceBeauty);
        mBeautyPannelView.setBeautyParamsChangeListener(this);
        mControllerLayout = (LinearLayout) view.findViewById(R.id.controller_container);
        view.findViewById(R.id.rtmproom_beauty_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBeautyPannelView.setVisibility(mBeautyPannelView.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
                mControllerLayout.setVisibility(mBeautyPannelView.getVisibility() == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
            }
        });

        //日志
        (view.findViewById(R.id.rtmproom_log_switcher_btn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchLog();
            }
        });


        Bundle bundle = getArguments();
        boolean createRoom = bundle.getBoolean("createRoom");
        if (createRoom) {

        }
        else {
            (view.findViewById(R.id.camera_switch_view)).setVisibility(View.GONE);
            (view.findViewById(R.id.beauty_btn_view)).setVisibility(View.GONE);
            view.findViewById(R.id.mute_btn_view).setVisibility(View.GONE);
        }

        //发送消息
        (view.findViewById(R.id.rtmproom_chat_btn)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInputMsgDialog();
            }
        });

        mTextMsgInputDialog = new TextMsgInputDialog(mActivity, R.style.InputDialog);
        mTextMsgInputDialog.setmOnTextSendListener(new TextMsgInputDialog.OnTextSendListener() {
            @Override
            public void onTextSend(String msg, boolean tanmuOpen) {
                sendMessage(msg);
            }
        });

        chatListView = ((ListView) view.findViewById(R.id.chat_list_view));
        textChatMsgList = new ArrayList<>();
        chatMessageAdapter = new ChatMessageAdapter(mActivity, textChatMsgList);
        chatListView.setAdapter(chatMessageAdapter);

        RelativeLayout chatViewLayout = (RelativeLayout)view.findViewById(R.id.video_view_3);
        mSwipeAnimationController = new SwipeAnimationController(mActivity);
        mSwipeAnimationController.setAnimationView(chatViewLayout);

        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mControllerLayout.setVisibility(View.VISIBLE);
                mBeautyPannelView.setVisibility(View.INVISIBLE);
                return mSwipeAnimationController.processEvent(event);
            }
        });

        chatListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mControllerLayout.setVisibility(View.VISIBLE);
                mBeautyPannelView.setVisibility(View.INVISIBLE);
                return false;
            }
        });

        mActivity.findViewById(R.id.rtc_multi_room_global_log_textview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mControllerLayout.setVisibility(View.VISIBLE);
                mBeautyPannelView.setVisibility(View.INVISIBLE);
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
        views[0].setLogMargin(12, 12, 80, 60);
        //views[1] = ((TXCloudVideoView) mActivity.findViewById(R.id.rtmproom_video_1));

        TextView nameViews[] = new TextView[6];
        nameViews[0] = ((TextView) mActivity.findViewById(R.id.rtmproom_video_name_0));
        //nameViews[1] = ((TextView) mActivity.findViewById(R.id.rtmproom_video_name_1));

        for (int i = 0; i < 1; i++) {
            mVideoViewsVector.add(new RoomVideoView(views[i], nameViews[i], null));
        }
    }

    private void addMessageItem(String userName, String message, TextChatMsg.Aligment aligment){

        TextChatMsg msg = new TextChatMsg(userName, TIME_FORMAT.format(new Date()), message, aligment);
        textChatMsgList.add(msg);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                chatMessageAdapter.notifyDataSetChanged();
                chatListView.post(new Runnable() {
                    @Override
                    public void run() {
                        chatListView.setSelection(textChatMsgList.size() - 1);
                    }
                });
            }
        });
    }

    private void sendMessage(final String message){

        myInterface.getLiveRoom().sendRoomTextMsg(message, new LiveRoom.SendTextMessageCallback() {
            @Override
            public void onError(int errCode, String errInfo) {
                new AlertDialog.Builder(mActivity, R.style.RtmpRoomDialogTheme).setMessage(errInfo)
                        .setTitle("发送消息失败")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        }).show();
            }

            @Override
            public void onSuccess() {
                addMessageItem(myInterface.getSelfUserName(), message, TextChatMsg.Aligment.LEFT);
            }
        });
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

        if (createRoom){
            myInterface.getLiveRoom().startLocalPreview(videoView.view);
            myInterface.getLiveRoom().setPauseImage(BitmapFactory.decodeResource(getResources(), R.drawable.pause_publish));
            myInterface.getLiveRoom().setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
            myInterface.getLiveRoom().setMute(mPusherMute);

            myInterface.getLiveRoom()
                    .createRoom(roomInfo.roomName, new LiveRoom.CreateRoomCallback() {
                        @Override
                        public void onSuccess(String roomId) {
                            roomInfo.roomID = roomId;
                        }

                        @Override
                        public void onError(int errCode, String e) {
                            errorGoBack("创建直播间错误", errCode, e);
                        }
                    });
        }else {
            myInterface.getLiveRoom()
                    .enterRoom(this.roomInfo.roomID, videoView.view, new LiveRoom.EnterRoomCallback() {
                        @Override
                        public void onError(int errCode, String errInfo) {
                            errorGoBack("进入直播间错误", errCode, errInfo);
                        }

                        @Override
                        public void onSuccess() {
                            LiveRoomChatFragment.this.roomInfo = roomInfo;
                            LiveRoomChatFragment.this.members.add(userID);
                        }
                    });
        }
    }

    private void errorGoBack(String title, int errCode, String errInfo){
        myInterface.getLiveRoom().exitRoom(null);

        new AlertDialog.Builder(mActivity)
                .setTitle(title)
                .setMessage(errInfo + "[" + errCode + "]")
                .setNegativeButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        backStack();
                    }
                }).show();
    }

    @Override
    public void onDestroyView() {
        mVideoViewsVector.clear();
        myInterface.getLiveRoom().stopLocalPreview();
        super.onDestroyView();
    }

    @Override
    public void onAttach(Context context) {
        if (BuildConfig.DEBUG)
            Log.i(TAG, "onAttach() called ");

        super.onAttach(context);

        myInterface = ((LiveRoomActivityInterface) context);
        mActivity = ((Activity) context);
    }

    @Override
    public void onAttach(Activity activity) {
        Log.i(TAG, "onAttach() called with: activity = [" + activity + "]");

        myInterface = ((LiveRoomActivityInterface) activity);
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

        myInterface.getLiveRoom().switchToForeground();
    }

    @Override
    public void onPause() {
        if (BuildConfig.DEBUG)
            Log.i(TAG, "onPause() called");

        super.onPause();

        myInterface.getLiveRoom().switchToBackground();
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
            myInterface.getLiveRoom().exitRoom(new LiveRoom.ExitRoomCallback() {
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
    public void onRecvRoomTextMsg(String roomid, String userid, String userName, String userAvatar, String message) {
        addMessageItem(userName, message, TextChatMsg.Aligment.LEFT);
    }

    @Override
    public void onRoomClosed(String roomId) {
        boolean createRoom =  getArguments().getBoolean("createRoom");
        if (createRoom == false) {
            new HintDialog.Builder(mActivity)
                    .setTittle("系统消息")
                    .setContent(String.format("直播间【%s】解散了", roomInfo != null ? roomInfo.roomName : "null"))
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
    public void onError(final int errorCode, final String errorMessage) {
        errorGoBack("直播间错误", errorCode, errorMessage);
    }

    @Override
    public void onBeautyParamsChange(BeautySettingPannel.BeautyParams params, int key) {
        LiveRoom liveRoom = myInterface.getLiveRoom();
        switch (key) {
            case BeautySettingPannel.BEAUTYPARAM_EXPOSURE:
                if (liveRoom != null) {
                    liveRoom.setExposureCompensation(params.mExposure);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY:
                mBeautyLevel = params.mBeautyLevel;
                if (liveRoom != null) {
                    liveRoom.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_WHITE:
                mWhiteningLevel = params.mWhiteLevel;
                if (liveRoom != null) {
                    liveRoom.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BIG_EYE:
                if (liveRoom != null) {
                    liveRoom.setEyeScaleLevel(params.mBigEyeLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACE_LIFT:
                if (liveRoom != null) {
                    liveRoom.setFaceSlimLevel(params.mFaceSlimLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FILTER:
                if (liveRoom != null) {
                    liveRoom.setFilter(params.mFilterBmp);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_GREEN:
                if (liveRoom != null) {
                    liveRoom.setGreenScreenFile(params.mGreenFile);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_MOTION_TMPL:
                if (liveRoom != null) {
                    liveRoom.setMotionTmpl(params.mMotionTmplPath);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_RUDDY:
                mRuddyLevel = params.mRuddyLevel;
                if (liveRoom != null) {
                    liveRoom.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_BEAUTY_STYLE:
                mBeautyStyle = params.mBeautyStyle;
                if (liveRoom != null) {
                    liveRoom.setBeautyFilter(mBeautyStyle, mBeautyLevel, mWhiteningLevel, mRuddyLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACEV:
                if (liveRoom != null) {
                    liveRoom.setFaceVLevel(params.mFaceVLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FACESHORT:
                if (liveRoom != null) {
                    liveRoom.setFaceShortLevel(params.mFaceShortLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_CHINSLIME:
                if (liveRoom != null) {
                    liveRoom.setChinLevel(params.mChinSlimLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_NOSESCALE:
                if (liveRoom != null) {
                    liveRoom.setNoseSlimLevel(params.mNoseScaleLevel);
                }
                break;
            case BeautySettingPannel.BEAUTYPARAM_FILTER_MIX_LEVEL:
                if (liveRoom != null) {
                    liveRoom.setSpecialRatio(params.mFilterMixLevel/10.f);
                }
                break;
//            case BeautySettingPannel.BEAUTYPARAM_CAPTURE_MODE:
//                if (mLivePusher != null) {
//                    boolean bEnable = ( 0 == params.mCaptureMode ? false : true);
//                    mLivePusher.enableHighResolutionCapture(bEnable);
//                }
//                break;
//            case BeautySettingPannel.BEAUTYPARAM_SHARPEN:
//                if (mLivePusher != null) {
//                    mLivePusher.setSharpenLevel(params.mSharpenLevel);
//                }
//                break;
        }
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

    private void showInputMsgDialog() {
        WindowManager windowManager = mActivity.getWindowManager();
        Display display = windowManager.getDefaultDisplay();
        WindowManager.LayoutParams lp = mTextMsgInputDialog.getWindow().getAttributes();

        lp.width = (display.getWidth()); //设置宽度
        mTextMsgInputDialog.getWindow().setAttributes(lp);
        mTextMsgInputDialog.setCancelable(true);
        mTextMsgInputDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        mTextMsgInputDialog.show();
    }
}

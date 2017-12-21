package com.tencent.liteav.demo.rtcroom.ui.double_room.fragment;

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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.liteav.demo.BuildConfig;
import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.rtcroom.IRTCRoomListener;
import com.tencent.liteav.demo.roomutil.commondef.PusherInfo;
import com.tencent.liteav.demo.roomutil.commondef.RoomInfo;
import com.tencent.liteav.demo.rtcroom.RTCRoom;
import com.tencent.liteav.demo.roomutil.commondef.TextChatMsg;
import com.tencent.liteav.demo.rtcroom.ui.double_room.RTCDoubleRoomActivityInterface;
import com.tencent.liteav.demo.roomutil.widget.ChatMessageAdapter;
import com.tencent.liteav.demo.roomutil.misc.HintDialog;
import com.tencent.rtmp.TXLiveConstants;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RTCDoubleRoomChatFragment extends Fragment implements IRTCRoomListener {

    private static final String TAG = RTCDoubleRoomChatFragment.class.getSimpleName();

    private Activity mActivity;
    private RTCDoubleRoomActivityInterface myInterface;

    private List<String> members = new ArrayList<>();

    private List<RoomVideoView> mVideoViewsVector = new ArrayList<>();
    private String userID;
    private String userName;
    private RoomInfo roomInfo;
    private View controlViewLayout;
    private ListView chatListView;
    private EditText chatEditText;
    private ArrayList<TextChatMsg> textChatMsgList;
    private ChatMessageAdapter chatMessageAdapter;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
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

    public static RTCDoubleRoomChatFragment newInstance(RoomInfo config, String userID, boolean createRoom) {
        RTCDoubleRoomChatFragment fragment = new RTCDoubleRoomChatFragment();
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
        View view = inflater.inflate(R.layout.fragment_rtc_double_room_chat, container, false);
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

        chatEditText = ((EditText) view.findViewById(R.id.double_room_chat_input_et));
        chatEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND){
                    CharSequence text = v.getText();
                    if (text == null) return false;
                    chatEditText.setText("");
                    sendMessage(text.toString());
                    return true;
                }
                return false;
            }
        });

        chatEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    InputMethodManager inputMethodManager = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        });

        chatListView = ((ListView) view.findViewById(R.id.chat_list_view));
        textChatMsgList = new ArrayList<>();
        chatMessageAdapter = new ChatMessageAdapter(mActivity, textChatMsgList);
        chatListView.setAdapter(chatMessageAdapter);

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

        TextView nameViews[] = new TextView[6];
        nameViews[0] = ((TextView) mActivity.findViewById(R.id.rtmproom_video_name_0));
        nameViews[1] = ((TextView) mActivity.findViewById(R.id.rtmproom_video_name_1));

        for (int i = 0; i < 2; i++) {
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

        myInterface.getRTCRoom().sendRoomTextMsg(message, new RTCRoom.SendTextMessageCallback() {
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
                addMessageItem(myInterface.getSelfUserName(), message, TextChatMsg.Aligment.RIGHT);
            }
        });
    }

    @Override
    public void onRecvRoomTextMsg(String roomid, String userid, String userName, String userAvatar, String msg) {
        addMessageItem(userName, msg, TextChatMsg.Aligment.LEFT);
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
        myInterface.getRTCRoom().setBitrateRange(400, 800);
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
        }else {
            myInterface.getRTCRoom().enterRoom(this.roomInfo.roomID, new RTCRoom.EnterRoomCallback() {
                        @Override
                        public void onError(int errCode, String errInfo) {
                            errorGoBack("进入会话错误", errCode, errInfo);
                        }

                        @Override
                        public void onSuccess() {
                            RTCDoubleRoomChatFragment.this.roomInfo = roomInfo;
                            RTCDoubleRoomChatFragment.this.members.add(userID);
                        }
                    });
        }
    }

    private void errorGoBack(String title, int errCode, String errInfo){
        myInterface.getRTCRoom().exitRoom(null);

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
        myInterface.getRTCRoom().stopLocalPreview();
        super.onDestroyView();
    }

    @Override
    public void onAttach(Context context) {
        if (BuildConfig.DEBUG)
            Log.i(TAG, "onAttach() called ");

        super.onAttach(context);

        myInterface = ((RTCDoubleRoomActivityInterface) context);
        mActivity = ((Activity) context);
    }

    @Override
    public void onAttach(Activity activity) {
        Log.i(TAG, "onAttach() called with: activity = [" + activity + "]");

        myInterface = ((RTCDoubleRoomActivityInterface) activity);
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
            new HintDialog.Builder(mActivity)
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



}

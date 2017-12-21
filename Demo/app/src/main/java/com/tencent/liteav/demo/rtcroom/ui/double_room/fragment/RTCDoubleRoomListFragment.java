package com.tencent.liteav.demo.rtcroom.ui.double_room.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.liteav.demo.BuildConfig;
import com.tencent.liteav.demo.R;
import com.tencent.liteav.demo.rtcroom.RTCRoom;
import com.tencent.liteav.demo.roomutil.commondef.RoomInfo;
import com.tencent.liteav.demo.roomutil.misc.NameGenerator;
import com.tencent.liteav.demo.rtcroom.ui.double_room.RTCDoubleRoomActivityInterface;
import com.tencent.liteav.demo.roomutil.widget.RoomListViewAdapter;
import com.tencent.liteav.demo.roomutil.misc.HintDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class RTCDoubleRoomListFragment extends Fragment {

    private static final String TAG = RTCDoubleRoomListFragment.class.getSimpleName();

    private List<RoomInfo> rooms = new ArrayList<>();
    private SwipeRefreshLayout mRefreshView;
    private RTCDoubleRoomActivityInterface myInterface;
    private Activity activity;
    private Button createRoomButton;
    private ListView mRoomListView;
    private RoomListViewAdapter roomListViewAdapter;
    private TextView tipTextView;
    private TextView nullListTipView;

    public static RTCDoubleRoomListFragment newInstance(String userID) {

        Bundle args = new Bundle();
        args.putString("userID", userID);
        RTCDoubleRoomListFragment fragment = new RTCDoubleRoomListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public RTCDoubleRoomListFragment() {
    }

    boolean enableLog = false;
    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Log.i(TAG, "onCreateView() called with: inflater = [" + inflater + "], container = [" + container
                + "], savedInstanceState = [" + savedInstanceState + "]");

        View v = inflater.inflate(R.layout.fragment_rtc_double_room_list, container, false);
        if (myInterface != null){
            myInterface.showGlobalLog(false);
        }

        tipTextView = ((TextView) v.findViewById(R.id.rtmproom_tip_textview));
        nullListTipView = ((TextView) v.findViewById(R.id.rtmproom_tip_null_list_textview));

        v.findViewById(R.id.rtmproom_log).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableLog = !enableLog;
                myInterface.showGlobalLog(enableLog);
            }
        });

        v.findViewById(R.id.rtmproom_help).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://cloud.tencent.com/document/product/454/12521"));
                startActivity(intent);
            }
        });

        mRoomListView = ((ListView) v.findViewById(R.id.rtmproom_room_listview));

        mRefreshView = ((SwipeRefreshLayout) v.findViewById(R.id.rtmproom_swiperefresh));
        mRefreshView.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                freshRooms();
            }
        });

        createRoomButton = ((Button) v.findViewById(R.id.rtmproom_create_room_button));
        createRoomButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreateDialog();
            }
        });

        roomListViewAdapter = new RoomListViewAdapter();
        roomListViewAdapter.setDataList(rooms);
        roomListViewAdapter.setRoomType(RoomListViewAdapter.ROOM_TYPE_DOUBLE);
        mRoomListView.setAdapter(roomListViewAdapter);
        mRoomListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (rooms.size() > position) {
                    final RoomInfo roomInfo = rooms.get(position);
                    enterRoomFragment(roomInfo, myInterface.getSelfUserID(), false);
                }
            }
        });

        return v;
    }

    private void enterRoomFragment(final RoomInfo roomInfo, final String userID, final boolean requestCreateRoom) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                RTCDoubleRoomChatFragment roomFragment = RTCDoubleRoomChatFragment.newInstance(roomInfo, userID, requestCreateRoom);
                FragmentManager fm = activity.getFragmentManager();
                FragmentTransaction ts = fm.beginTransaction();
                ts.replace(R.id.rtmproom_fragment_container, roomFragment);
                ts.addToBackStack(null);
                ts.commit();
            }
        });

    }

    private void showCreateDialog(){
        final View view = LayoutInflater.from(activity)
                .inflate(R.layout.layout_rtmproom_dialog_create_room, null, false);
        EditText et = (EditText) view.findViewById(R.id.rtmproom_dialog_create_room_edittext);
        et.setHint("请输入会话名称");
        new AlertDialog.Builder(activity, R.style.RtmpRoomDialogTheme)
                .setView(view)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        EditText et = (EditText) view.findViewById(R.id.rtmproom_dialog_create_room_edittext);
                        Editable text = et.getText();
                        if (text != null) {
                            String name = NameGenerator.replaceNonPrintChar(text.toString(), -1, null, false);
                            if (name != null && name.length() > 0) {
                                if (myInterface.getSelfUserID() != null) {
                                    createRoom(name);
                                }
                                InputMethodManager m = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                                m.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);
                                dialog.dismiss();
                                return;
                            }
                        }
                        Toast.makeText(activity.getApplicationContext(), "会话名称不能为空", Toast.LENGTH_SHORT).show();

                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        InputMethodManager m =(InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                        m.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);
                        dialog.dismiss();
                    }
                }).create().show();
    }

    private void createRoom(final String roomName) {

        if (BuildConfig.DEBUG)
            Log.i(TAG, "createRoom() called with: roomName = [" + roomName + "]");

        final RoomInfo roomInfo = new RoomInfo();
        roomInfo.roomName = roomName;
        enterRoomFragment(roomInfo, myInterface.getSelfUserID(), true);
    }

    @Override
    public void onAttach(Context context) {
        Log.i(TAG, "onAttach() called with: context = [" + context + "]");
        myInterface = ((RTCDoubleRoomActivityInterface) context);
        activity = ((Activity) context);
        super.onAttach(context);
    }

    /**
     * 国内低端手机，低版本兼容问题
     * @param activity
     */
    @Override
    public void onAttach(Activity activity) {
        Log.i(TAG, "onAttach() called with: activity = [" + activity + "]");
        super.onAttach(activity);
        myInterface = ((RTCDoubleRoomActivityInterface) activity);
        this.activity = activity;
    }


    @Override
    public void onResume() {
        super.onResume();

        myInterface.setTitle("双人聊天");
        freshRooms();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void freshRooms() {
        if (myInterface == null ) {
            myInterface = ((RTCDoubleRoomActivityInterface) getActivity());
            if (myInterface == null) return;
            return;
        }

        if (!isVisible()) return;

        myInterface.getRTCRoom().getRoomList(0, 20, new RTCRoom.GetRoomListCallback() {
            @Override
            public void onSuccess(ArrayList<RoomInfo> data) {

                mRefreshView.setRefreshing(false);
                nullListTipView.setVisibility(View.GONE);
                rooms.clear();
                if (data != null && data.size() > 0){

                    nullListTipView.setVisibility(View.GONE);
                    tipTextView.setVisibility(View.VISIBLE);
                    rooms.addAll(data);
                }else{
                    tipTextView.setVisibility(View.GONE);
                    nullListTipView.setVisibility(View.VISIBLE);
                }
                roomListViewAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(int errCode, String e) {
                mRefreshView.setRefreshing(false);
                nullListTipView.setVisibility(View.VISIBLE);

                new HintDialog.Builder(activity)
                        .setTittle("获取会话列表失败")
                        .setContent(e)
                        .show();
            }
        });
    }


}

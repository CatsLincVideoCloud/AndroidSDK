package com.tencent.liteav.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.tencent.liteav.demo.common.widget.ModuleEntryItemView;
import com.tencent.liteav.demo.play.LivePlayerActivity;
import com.tencent.liteav.demo.play.VodPlayerActivity;
import com.tencent.liteav.demo.push.LivePublisherActivity;
import com.tencent.liteav.demo.rtcroom.ui.double_room.RTCDoubleRoomActivity;
import com.tencent.liteav.demo.rtcroom.ui.multi_room.RTCMultiRoomActivity;
import com.tencent.liteav.demo.liveroom.ui.LiveRoomActivity;
import com.tencent.liteav.demo.shortvideo.choose.TCVideoChooseActivity;
import com.tencent.liteav.demo.videocall.VideoCallActivity;
import com.tencent.liteav.demo.videorecord.TCVideoSettingActivity;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getName();
    private ListView mListView;
    private int mSelectedModuleId = 0;
    private ModuleEntryItemView mSelectedView;
    private TextView mMainTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            Log.d(TAG, "brought to front");
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        mMainTitle = (TextView)findViewById(R.id.main_title);
        mMainTitle.setText("视频云 SDK DEMO ");
        mListView = (ListView) findViewById(R.id.entry_lv);
        EntryAdapter adapter = new EntryAdapter();
        mListView.setAdapter(adapter);
    }

    private class EntryAdapter extends BaseAdapter {

        public class ItemInfo {
            String mName;
            int mIconId;
            Class mClass;

            public ItemInfo(String name, int iconId, Class c) {
                mName = name;
                mIconId = iconId;
                mClass = c;
            }
        }

        private ArrayList<ItemInfo> mData = new ArrayList<>();

        public EntryAdapter() {
            super();
            createData();
        }

        private void createData() {
            mData.add(new ItemInfo("直播体验室", R.drawable.room_live, LiveRoomActivity.class));
            mData.add(new ItemInfo("双人音视频", R.drawable.room_double,  RTCDoubleRoomActivity.class));
            mData.add(new ItemInfo("多人音视频", R.drawable.room_multi, RTCMultiRoomActivity.class));
            mData.add(new ItemInfo("点播播放器", R.drawable.play, VodPlayerActivity.class));
            mData.add(new ItemInfo("短视频录制", R.drawable.video, TCVideoSettingActivity.class));
            mData.add(new ItemInfo("短视频特效", R.drawable.cut, TCVideoChooseActivity.class));
            mData.add(new ItemInfo("短视频拼接", R.drawable.composite, TCVideoChooseActivity.class));
            mData.add(new ItemInfo("RTMP 推流", R.drawable.push, LivePublisherActivity.class));
            mData.add(new ItemInfo("直播播放器", R.drawable.live, LivePlayerActivity.class));
            mData.add(new ItemInfo("低延时播放", R.drawable.realtime_play,  LivePlayerActivity.class));
            mData.add(new ItemInfo("视频小派对", R.drawable.conf_icon, VideoCallActivity.class));
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Object getItem(int position) {
            return mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (null == convertView) {
                convertView = new ModuleEntryItemView(MainActivity.this);
            }
            ItemInfo info = (ItemInfo) getItem(position);
            ModuleEntryItemView v = (ModuleEntryItemView) convertView;
            v.setContent(info.mName, info.mIconId);
            v.setTag(info);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ItemInfo itemInfo = (ItemInfo) v.getTag();
                    Intent intent = new Intent(MainActivity.this, itemInfo.mClass);
                    intent.putExtra("TITLE", itemInfo.mName);
                    if (itemInfo.mIconId == R.drawable.play) {
                        intent.putExtra("PLAY_TYPE", LivePlayerActivity.ACTIVITY_TYPE_VOD_PLAY);
                    } else if (itemInfo.mIconId == R.drawable.live) {
                        intent.putExtra("PLAY_TYPE", LivePlayerActivity.ACTIVITY_TYPE_LIVE_PLAY);
                    } else if (itemInfo.mIconId == R.drawable.mic) {
                        intent.putExtra("PLAY_TYPE", LivePlayerActivity.ACTIVITY_TYPE_LINK_MIC);
                    } else if (itemInfo.mIconId == R.drawable.cut) {
                        intent.putExtra("CHOOSE_TYPE", TCVideoChooseActivity.TYPE_SINGLE_CHOOSE);
                    } else if (itemInfo.mIconId == R.drawable.composite) {
                        intent.putExtra("CHOOSE_TYPE", TCVideoChooseActivity.TYPE_MULTI_CHOOSE);
                    } else if (itemInfo.mIconId == R.drawable.conf_icon) {
//                        intent.putExtra("CHOOSE_TYPE", VideoCallActivity.TYPE_MULTI_CHOOSE);
                    } else if (itemInfo.mIconId == R.drawable.realtime_play) {
                        intent.putExtra("PLAY_TYPE", LivePlayerActivity.ACTIVITY_TYPE_REALTIME_PLAY);
                    }
                    if (mSelectedView != null) {
                        mSelectedView.setBackgroudId(R.drawable.block_normal);
                    }
                    mSelectedModuleId = itemInfo.mIconId;
                    mSelectedView = (ModuleEntryItemView)v;
                    mSelectedView.setBackgroudId(R.drawable.block_pressed);
                    MainActivity.this.startActivity(intent);
                }
            });
            if (mSelectedModuleId == info.mIconId) {
                mSelectedView = v;
                mSelectedView.setBackgroudId(R.drawable.block_pressed);
            }

            return convertView;
        }
    }
}

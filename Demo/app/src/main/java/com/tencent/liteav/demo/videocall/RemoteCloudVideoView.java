package com.tencent.liteav.demo.videocall;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.tencent.liteav.demo.R;
import com.tencent.rtmp.ui.TXCloudVideoView;

/**
 * Created by willguo on 15/09/2017.
 */

public class RemoteCloudVideoView extends TXCloudVideoView {
    TextView mTag;

    public RemoteCloudVideoView(Context context) {
        super(context);
        mTag = new TextView(context);
        mTag.setLayoutParams(new FrameLayout.LayoutParams(100, 100));
        mTag.setBackgroundResource(R.drawable.music_item_selected);
        mTag.setVisibility(View.VISIBLE);
    }


    public void showTag(boolean show) {
        if (show) {
            removeView(mTag);
            addView(mTag);
            mTag.setVisibility(View.VISIBLE);
        } else {
            removeView(mTag);
            mTag.setVisibility(View.INVISIBLE);
        }

    }


}

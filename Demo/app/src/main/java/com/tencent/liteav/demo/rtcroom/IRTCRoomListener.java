package com.tencent.liteav.demo.rtcroom;

import android.support.annotation.Nullable;

import com.tencent.liteav.demo.roomutil.commondef.PusherInfo;

import java.util.List;

/**
 * Created by jac on 2017/10/30.
 */

public interface IRTCRoomListener {

    /**
     * 获取房间成员通知
     * @param pusherInfoList
     */
    void onGetPusherList(List<PusherInfo> pusherInfoList);

    /**
     * 新成员加入房间通知
     * @param pusherInfo
     */

    void onPusherJoin(PusherInfo pusherInfo);
    /**
     * 成员离开房间通知
     * @param pusherInfo
     */

    void onPusherQuit(PusherInfo pusherInfo);

    /**
     * 收到房间解散通知
     * @param roomId
     */

    void onRoomClosed(String roomId);

    /**
     * 日志回调
     * @param log
     */
    void onDebugLog(String log);

    /**
     *
     * @param roomid
     * @param userid
     * @param userName
     * @param userAvatar
     * @param msg
     */
    void onRecvRoomTextMsg(String roomid, String userid, String userName, String userAvatar, String msg);

    /**
     * 错误回调
     * @param errorCode
     * @param errorMessage
     */
    void onError(int errorCode, String errorMessage);
}

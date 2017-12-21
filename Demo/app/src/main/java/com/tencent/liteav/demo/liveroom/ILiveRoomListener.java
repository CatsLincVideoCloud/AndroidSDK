package com.tencent.liteav.demo.liveroom;

/**
 * Created by dennyfeng on 2017/11/22.
 */

public interface ILiveRoomListener {
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
     * 收到文本消息
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

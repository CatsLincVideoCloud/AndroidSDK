package com.tencent.liteav.demo.roomutil.commondef;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by jac on 2017/10/30.
 */

public class PusherInfo implements Parcelable {

    public String   userID;
    public String   userName;
    public String   userAvatar;
    public String   accelerateURL;


    public PusherInfo() {

    }

    public PusherInfo(String userID, String userName, String userAvatar, String accelerateURL) {
        this.userID = userID;
        this.userName = userName;
        this.userAvatar = userAvatar;
        this.accelerateURL = accelerateURL;
    }

    protected PusherInfo(Parcel in) {
        this.userID = in.readString();
        this.userName = in.readString();
        this.accelerateURL = in.readString();
        this.userAvatar = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.userID);
        dest.writeString(this.userName);
        dest.writeString(this.accelerateURL);
        dest.writeString(this.userAvatar);
    }

    public static final Parcelable.Creator<PusherInfo> CREATOR = new Parcelable.Creator<PusherInfo>() {
        @Override
        public PusherInfo createFromParcel(Parcel source) {
            return new PusherInfo(source);
        }

        @Override
        public PusherInfo[] newArray(int size) {
            return new PusherInfo[size];
        }
    };

    @Override
    public int hashCode() {
        return userID.hashCode();
    }

    @Override
    public String toString() {
        return "PusherInfo{" +
                "userID='" + userID + '\'' +
                ", userName='" + userName + '\'' +
                ", accelerateURL='" + accelerateURL + '\'' +
                ", userAvatar='" + userAvatar + '\'' +
                '}';
    }
}

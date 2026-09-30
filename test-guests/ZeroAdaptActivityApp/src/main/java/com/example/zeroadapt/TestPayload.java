package com.example.zeroadapt;

import android.os.Parcel;
import android.os.Parcelable;

public final class TestPayload implements Parcelable {
    public final String value;
    public TestPayload(String value) { this.value = value; }
    private TestPayload(Parcel in) { value = in.readString(); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int flags) { out.writeString(value); }
    public static final Creator<TestPayload> CREATOR = new Creator<TestPayload>() {
        public TestPayload createFromParcel(Parcel in) { return new TestPayload(in); }
        public TestPayload[] newArray(int size) { return new TestPayload[size]; }
    };
}

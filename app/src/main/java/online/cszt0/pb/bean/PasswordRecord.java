package online.cszt0.pb.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import online.cszt0.androidcommonutils.view.CommonAdapterInterface;
import online.cszt0.pb.utils.FilterUtils;

public class PasswordRecord implements SQLiteBean, Parcelable, CommonAdapterInterface.Filterable {
    private int rowid;
    private String title;
    private String account;
    private String password;
    private String detail;

    public static final Creator<PasswordRecord> CREATOR = new Creator<PasswordRecord>() {
        @Override
        public PasswordRecord createFromParcel(Parcel in) {
            return new PasswordRecord(in);
        }

        @Override
        public PasswordRecord[] newArray(int size) {
            return new PasswordRecord[size];
        }
    };

    public PasswordRecord() {
    }

    protected PasswordRecord(Parcel in) {
        rowid = in.readInt();
        title = in.readString();
        account = in.readString();
        password = in.readString();
        detail = in.readString();
    }

    public int getRowid() {
        return rowid;
    }

    public void setRowid(int rowid) {
        this.rowid = rowid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(rowid);
        dest.writeString(title);
        dest.writeString(account);
        dest.writeString(password);
        dest.writeString(detail);
    }

    @Override
    public boolean filter(CharSequence constraint) {
        return FilterUtils.containsKeyword(constraint.toString(), title, detail);
    }
}

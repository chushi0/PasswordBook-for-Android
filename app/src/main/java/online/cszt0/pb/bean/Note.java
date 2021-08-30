package online.cszt0.pb.bean;

import android.os.Parcel;
import android.os.Parcelable;

public class Note implements SQLiteBean, Parcelable {
    private static final int TITLE_MAX_LENGTH = 50;

    private int rowid;
    private String title;
    private String content;

    public static final Creator<Note> CREATOR = new Creator<Note>() {
        @Override
        public Note createFromParcel(Parcel in) {
            return new Note(in);
        }

        @Override
        public Note[] newArray(int size) {
            return new Note[size];
        }
    };

    public Note() {
    }

    protected Note(Parcel in) {
        rowid = in.readInt();
        title = in.readString();
        content = in.readString();
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(rowid);
        dest.writeString(title);
        dest.writeString(content);
    }

    public void computeTitle() {
        if (content.length() < TITLE_MAX_LENGTH) {
            title = content;
        } else {
            title = content.substring(0, TITLE_MAX_LENGTH);
        }
    }
}

package online.cszt0.pb.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

import online.cszt0.pb.BuildConfig;
import online.cszt0.pb.bean.Note;
import online.cszt0.pb.bean.PasswordRecord;
import online.cszt0.pb.ui.UpgradeActivity;

public class Database {
    private static final int VERSION = 2;

    private Database() {
    }

    public static boolean isInitialize(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        return preferences.getBoolean("pb-init", false);
    }

    public static void initializeDatabase(Context context, byte[] key) {
        Pair<String, String> encode = PasswordUtils.encodePassword(key);
        SharedPreferences preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        preferences.edit()
                .putBoolean("pb-init", true)
                .putString("uuid", encode.first)
                .putString("verify", encode.second)
                .putInt("version", VERSION)
                .apply();
    }

    public static boolean checkPassword(Context context, byte[] key) {
        SharedPreferences preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        String uuid = preferences.getString("uuid", null);
        String verify = preferences.getString("verify", null);
        return PasswordUtils.checkPassword(key, uuid, verify);
    }

    private static SQLiteDatabase openDatabase(Context context) {
        SQLiteDatabase sqLiteDatabase = context.openOrCreateDatabase("pb.db", Context.MODE_PRIVATE, null);
        sqLiteDatabase.execSQL("create table if not exists book(title, account, password, detail)");
        sqLiteDatabase.execSQL("create table if not exists notes(title, content)");
        return sqLiteDatabase;
    }

    public static List<PasswordRecord> listRecord(Context context, byte[] key) {
        try (SQLiteDatabase db = openDatabase(context);
             Cursor cursor = db.rawQuery("select rowid, title from book", new String[0])) {
            List<PasswordRecord> list = new ArrayList<>();
            while (cursor.moveToNext()) {
                PasswordRecord record = new PasswordRecord();
                record.setRowid(cursor.getInt(0));
                record.setTitle(Crypto.decryptData(cursor.getString(1), key));
                list.add(record);
            }
            return list;
        }
    }

    public static boolean readRecord(Context context, PasswordRecord record, byte[] key) {
        if (BuildConfig.DEBUG && record.getRowid() == 0) {
            throw new IllegalArgumentException();
        }
        try (SQLiteDatabase db = openDatabase(context);
             Cursor cursor = db.rawQuery("select title, account, password, detail from book where rowid=?", new String[]{String.valueOf(record.getRowid())})) {
            if (cursor.moveToNext()) {
                record.setTitle(Crypto.decryptData(cursor.getString(0), key));
                record.setAccount(Crypto.decryptData(cursor.getString(1), key));
                record.setPassword(Crypto.decryptData(cursor.getString(2), key));
                record.setDetail(Crypto.decryptData(cursor.getString(3), key));
                return true;
            }
            return false;
        }
    }

    public static void saveRecord(Context context, PasswordRecord record, byte[] key) {
        try (SQLiteDatabase db = openDatabase(context)) {
            if (record.getRowid() == 0) {
                db.execSQL("insert into book (title, account, password, detail) values(?,?,?,?)", new Object[]{
                        Crypto.encryptData(record.getTitle(), key),
                        Crypto.encryptData(record.getAccount(), key),
                        Crypto.encryptData(record.getPassword(), key),
                        Crypto.encryptData(record.getDetail(), key)
                });
                try (Cursor cursor = db.rawQuery("select last_insert_rowid()", new String[0])) {
                    if (cursor.moveToNext()) {
                        record.setRowid(cursor.getInt(0));
                    }
                }
            } else {
                db.execSQL("update book set title=?, account=?, password=?, detail=? where rowid=?", new Object[]{
                        Crypto.encryptData(record.getTitle(), key),
                        Crypto.encryptData(record.getAccount(), key),
                        Crypto.encryptData(record.getPassword(), key),
                        Crypto.encryptData(record.getDetail(), key),
                        record.getRowid()
                });
            }
        }
    }

    public static void removeRecord(Context context, PasswordRecord record) {
        try (SQLiteDatabase db = openDatabase(context)) {
            db.execSQL("delete from book where rowid=?", new Object[]{record.getRowid()});
        }
    }

    public static List<Note> listNote(Context context, byte[] key) {
        try (SQLiteDatabase db = openDatabase(context);
             Cursor cursor = db.rawQuery("select rowid, title from notes", new String[0])) {
            List<Note> noteList = new ArrayList<>();
            while (cursor.moveToNext()) {
                Note note = new Note();
                note.setRowid(cursor.getInt(0));
                note.setTitle(Crypto.decryptData(cursor.getString(1), key));
                noteList.add(note);
            }
            return noteList;
        }
    }

    public static boolean readNote(Context context, Note note, byte[] key) {
        if (BuildConfig.DEBUG && note.getRowid() == 0) {
            throw new IllegalArgumentException();
        }
        try (SQLiteDatabase db = openDatabase(context);
             Cursor cursor = db.rawQuery("select title, content from notes where rowid=?", new String[]{String.valueOf(note.getRowid())})) {
            if (cursor.moveToNext()) {
                note.setTitle(Crypto.decryptData(cursor.getString(0), key));
                note.setContent(Crypto.decryptData(cursor.getString(1), key));
                return true;
            }
        }
        return false;
    }

    public static void saveNote(Context context, Note note, byte[] key) {
        note.computeTitle();
        try (SQLiteDatabase db = openDatabase(context)) {
            if (note.getRowid() == 0) {
                db.execSQL("insert into notes (title, content) values(?,?)", new Object[]{
                        Crypto.encryptData(note.getTitle(), key),
                        Crypto.encryptData(note.getContent(), key),
                });
                try (Cursor cursor = db.rawQuery("select last_insert_rowid()", new String[0])) {
                    if (cursor.moveToNext()) {
                        note.setRowid(cursor.getInt(0));
                    }
                }
            } else {
                db.execSQL("update notes set title=?, content=? where rowid=?", new Object[]{
                        Crypto.encryptData(note.getTitle(), key),
                        Crypto.encryptData(note.getContent(), key),
                        note.getRowid()
                });
            }
        }
    }

    public static void removeNote(Context context, Note note) {
        try (SQLiteDatabase db = openDatabase(context)) {
            db.execSQL("delete from notes where rowid=?", new Object[]{note.getRowid()});
        }
    }

    public static void resaveAll(Context context, byte[] oldKey, byte[] newKey) {
        // 数据库更新
        try (SQLiteDatabase db = openDatabase(context)) {
            db.beginTransaction();
            try {
                try (Cursor cursor = db.rawQuery("select rowid, title, account, password, detail from book", new String[0])) {
                    while (cursor.moveToNext()) {
                        int rowid = cursor.getInt(0);
                        String title = Crypto.decryptData(cursor.getString(1), oldKey);
                        String account = Crypto.decryptData(cursor.getString(2), oldKey);
                        String password = Crypto.decryptData(cursor.getString(3), oldKey);
                        String detail = Crypto.decryptData(cursor.getString(4), oldKey);

                        db.execSQL("update book set title=?, account=?, password=?, detail=? where rowid=?", new Object[]{
                                Crypto.encryptData(title, newKey),
                                Crypto.encryptData(account, newKey),
                                Crypto.encryptData(password, newKey),
                                Crypto.encryptData(detail, newKey),
                                rowid,
                        });
                    }
                }
                try (Cursor cursor = db.rawQuery("select rowid, title, content from notes", new String[0])) {
                    while (cursor.moveToNext()) {
                        int rowid = cursor.getInt(0);
                        String title = Crypto.decryptData(cursor.getString(1), oldKey);
                        String content = Crypto.decryptData(cursor.getString(2), oldKey);

                        db.execSQL("update notes set title=?, content=? where rowid=?", new Object[]{
                                Crypto.encryptData(title, newKey),
                                Crypto.encryptData(content, newKey),
                                rowid
                        });
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
        // 密钥验证更新
        Pair<String, String> encode = PasswordUtils.encodePassword(newKey);
        SharedPreferences preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        preferences.edit()
                .putString("uuid", encode.first)
                .putString("verify", encode.second)
                .apply();
    }

    public static void importData(Context context, DataImportHelper dataImportHelper, byte[] key) {
        try (SQLiteDatabase db = openDatabase(context)) {
            db.beginTransaction();
            try {
                DataImportHelper.Provider provider = dataImportHelper.getProvider();
                try (DataImportHelper.BeanReader<PasswordRecord> recordReader = provider.openPasswordReader()) {
                    while (recordReader.hasNext()) {
                        PasswordRecord record = recordReader.next();
                        db.execSQL("insert into book (title, account, password, detail) values(?,?,?,?)", new Object[]{
                                Crypto.encryptData(record.getTitle(), key),
                                Crypto.encryptData(record.getAccount(), key),
                                Crypto.encryptData(record.getPassword(), key),
                                Crypto.encryptData(record.getDetail(), key)
                        });
                    }
                }
                try (DataImportHelper.BeanReader<Note> noteReader = provider.openNoteReader()) {
                    while (noteReader.hasNext()) {
                        Note note = noteReader.next();
                        db.execSQL("insert into notes (title, content) values (?, ?)", new Object[]{
                                Crypto.encryptData(note.getTitle(), key),
                                Crypto.encryptData(note.getContent(), key)
                        });
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }

    public static void dumpTo(Context context, SQLiteDatabase database, byte[] mainKey, byte[] key) {
        try (SQLiteDatabase db = openDatabase(context)) {
            database.beginTransaction();
            database.execSQL("create table if not exists book(title, account, password, detail)");
            database.execSQL("create table if not exists notes(title, content)");
            try {
                try (Cursor cursor = db.rawQuery("select title, account, password, detail from book", new String[0])) {
                    while (cursor.moveToNext()) {
                        String title = Crypto.decryptData(cursor.getString(0), mainKey);
                        String account = Crypto.decryptData(cursor.getString(1), mainKey);
                        String password = Crypto.decryptData(cursor.getString(2), mainKey);
                        String detail = Crypto.decryptData(cursor.getString(3), mainKey);

                        database.execSQL("insert into book (title, account, password, detail) values (?,?,?,?)", new Object[]{
                                Crypto.encryptData(title, key),
                                Crypto.encryptData(account, key),
                                Crypto.encryptData(password, key),
                                Crypto.encryptData(detail, key),
                        });
                    }
                }
                try (Cursor cursor = db.rawQuery("select title, content from notes", new String[0])) {
                    while (cursor.moveToNext()) {
                        String title = Crypto.decryptData(cursor.getString(0), mainKey);
                        String content = Crypto.decryptData(cursor.getString(1), mainKey);

                        database.execSQL("insert into notes (title, content) values (?,?)", new Object[]{
                                Crypto.encryptData(title, key),
                                Crypto.encryptData(content, key)
                        });
                    }
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }
    }

    public static void wipeAll(Context context) {
        SQLiteDatabase.deleteDatabase(context.getDatabasePath("pb.db"));
        SharedPreferences preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        preferences.edit().clear().apply();
    }

    public static boolean shouldUpgrade(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        return preferences.getInt("version", VERSION) < VERSION;
    }

    public static void upgrade(Context context) {
        Log.d("Upgrade", "upgrade");
        SharedPreferences preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        preferences.edit().putInt("version", VERSION).apply();
        SystemClock.sleep(500);
    }
}

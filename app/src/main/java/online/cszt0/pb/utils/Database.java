package online.cszt0.pb.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

import online.cszt0.pb.BuildConfig;
import online.cszt0.pb.bean.PasswordRecord;

public class Database {
    private static final int VERSION = 1;

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
        return sqLiteDatabase;
    }

    public static List<PasswordRecord> getPasswordRecordList(Context context, byte[] key) {
        try (SQLiteDatabase db = openDatabase(context);
             Cursor cursor = db.rawQuery("select rowid, title from book", new String[0])) {
            List<PasswordRecord> list = new ArrayList<>();
            while (cursor.moveToNext()) {
                PasswordRecord record = new PasswordRecord();
                record.setRowid(cursor.getInt(0));
                record.setTitle(Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(1)), key));
                list.add(record);
            }
            return list;
        }
    }

    public static boolean readDetail(Context context, PasswordRecord record, byte[] key) {
        if (BuildConfig.DEBUG && record.getRowid() == 0) {
            throw new IllegalArgumentException();
        }
        try (SQLiteDatabase db = openDatabase(context);
             Cursor cursor = db.rawQuery("select title, account, password, detail from book where rowid=?", new String[]{String.valueOf(record.getRowid())})) {
            if (cursor.moveToNext()) {
                record.setTitle(Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(0)), key));
                record.setAccount(Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(1)), key));
                record.setPassword(Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(2)), key));
                record.setDetail(Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(3)), key));
                return true;
            }
            return false;
        }
    }

    public static void saveDetail(Context context, PasswordRecord record, byte[] key) {
        try (SQLiteDatabase db = openDatabase(context)) {
            if (record.getRowid() == 0) {
                db.execSQL("insert into book (title, account, password, detail) values(?,?,?,?)", new Object[]{
                        Crypto.base64Encrypt(Crypto.encryptStringData(record.getTitle(), key)),
                        Crypto.base64Encrypt(Crypto.encryptStringData(record.getAccount(), key)),
                        Crypto.base64Encrypt(Crypto.encryptStringData(record.getPassword(), key)),
                        Crypto.base64Encrypt(Crypto.encryptStringData(record.getDetail(), key))
                });
                try (Cursor cursor = db.rawQuery("select last_insert_rowid()", new String[0])) {
                    if (cursor.moveToNext()) {
                        record.setRowid(cursor.getInt(0));
                    }
                }
            } else {
                db.execSQL("update book set title=?, account=?, password=?, detail=? where rowid=?", new Object[]{
                        Crypto.base64Encrypt(Crypto.encryptStringData(record.getTitle(), key)),
                        Crypto.base64Encrypt(Crypto.encryptStringData(record.getAccount(), key)),
                        Crypto.base64Encrypt(Crypto.encryptStringData(record.getPassword(), key)),
                        Crypto.base64Encrypt(Crypto.encryptStringData(record.getDetail(), key)),
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

    public static void resaveAll(Context context, byte[] oldKey, byte[] newKey) {
        // 数据库更新
        try (SQLiteDatabase db = openDatabase(context)) {
            db.beginTransaction();
            try (Cursor cursor = db.rawQuery("select rowid, title, account, password, detail from book", new String[0])) {
                while (cursor.moveToNext()) {
                    int rowid = cursor.getInt(0);
                    String title = Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(1)), oldKey);
                    String account = Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(2)), oldKey);
                    String password = Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(3)), oldKey);
                    String detail = Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(4)), oldKey);

                    db.execSQL("update book set title=?, account=?, password=?, detail=? where rowid=?", new Object[]{
                            Crypto.base64Encrypt(Crypto.encryptStringData(title, newKey)),
                            Crypto.base64Encrypt(Crypto.encryptStringData(account, newKey)),
                            Crypto.base64Encrypt(Crypto.encryptStringData(password, newKey)),
                            Crypto.base64Encrypt(Crypto.encryptStringData(detail, newKey)),
                            rowid,
                    });
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
            try (DataImportHelper.RecordReader recordReader = dataImportHelper.getData()) {
                while (recordReader.hasNext()) {
                    PasswordRecord record = recordReader.next();
                    db.execSQL("insert into book (title, account, password, detail) values(?,?,?,?)", new Object[]{
                            Crypto.base64Encrypt(Crypto.encryptStringData(record.getTitle(), key)),
                            Crypto.base64Encrypt(Crypto.encryptStringData(record.getAccount(), key)),
                            Crypto.base64Encrypt(Crypto.encryptStringData(record.getPassword(), key)),
                            Crypto.base64Encrypt(Crypto.encryptStringData(record.getDetail(), key))
                    });
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
            try (Cursor cursor = db.rawQuery("select title, account, password, detail from book", new String[0])) {
                while (cursor.moveToNext()) {
                    String title = Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(0)), mainKey);
                    String account = Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(1)), mainKey);
                    String password = Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(2)), mainKey);
                    String detail = Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(3)), mainKey);

                    database.execSQL("insert into book (title, account, password, detail) values (?,?,?,?)", new Object[]{
                            Crypto.base64Encrypt(Crypto.encryptStringData(title, key)),
                            Crypto.base64Encrypt(Crypto.encryptStringData(account, key)),
                            Crypto.base64Encrypt(Crypto.encryptStringData(password, key)),
                            Crypto.base64Encrypt(Crypto.encryptStringData(detail, key)),
                    });
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
}

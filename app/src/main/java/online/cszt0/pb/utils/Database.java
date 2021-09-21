package online.cszt0.pb.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import android.util.Pair;

import androidx.biometric.BiometricPrompt;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import online.cszt0.pb.BuildConfig;
import online.cszt0.pb.R;
import online.cszt0.pb.bean.Note;
import online.cszt0.pb.bean.PasswordRecord;

public class Database {
    private static final int VERSION = 2;

    private static final String KEY_NAME = "main-password";

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

    public static boolean checkBiometricOpen(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        return preferences.getBoolean("biometric-enable", false);
    }

    @SuppressLint("CheckResult")
    public static void enableBiometricPassword(Context context, Runnable onSuccess, Consumer<Throwable> onFail) {
        //noinspection ResultOfMethodCallIgnored
        MainPassword.getInstance().userKeyObservable(context)
                .observeOn(Schedulers.io())
                .doOnNext(key -> generateSecretKey(new KeyGenParameterSpec.Builder(
                        KEY_NAME,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                        .setUserAuthenticationRequired(true)
                        .setInvalidatedByBiometricEnrollment(true)
                        .setRandomizedEncryptionRequired(false)
                        .build()))
                .map(k -> {
                    Cipher cipher = getCipher();
                    SecretKey secretKey = getSecretKey();
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                    return new Pair<>(k, new BiometricPrompt.CryptoObject(cipher));
                })
                .flatMap(pair -> BiometricUtils.authenticateObservableSource(context, context.getString(R.string.text_biometric_enable_title), context.getString(R.string.text_biometric_enable_subtitle), pair.second, cryptoObject -> new Pair<>(pair.first, cryptoObject)))
                .map(pair -> new Pair<>(pair.first, pair.second.getCipher()))
                .doOnNext(pair -> {
                    byte[] encryptKey = pair.second.doFinal(pair.first);
                    byte[] iv = pair.second.getIV();
                    String strEncKey = Crypto.base64Encrypt(encryptKey);
                    String strIv = Crypto.base64Encrypt(iv);
                    SharedPreferences preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
                    preferences.edit()
                            .putBoolean("biometric-enable", true)
                            .putString("biometric-key", strEncKey)
                            .putString("biometric-iv", strIv)
                            .apply();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(cipherPair -> onSuccess.run(), onFail::accept);
    }

    public static void disableBiometricPassword(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        preferences.edit()
                .putBoolean("biometric-enable", false)
                .remove("biometric-key")
                .remove("biometric-iv")
                .apply();

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            keyStore.deleteEntry(KEY_NAME);
        } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException e) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
        }
    }

    public static BiometricPrompt.CryptoObject getCryptoObject(Context context) throws NoSuchAlgorithmException, NoSuchPaddingException, UnrecoverableKeyException, CertificateException, KeyStoreException, IOException, InvalidKeyException, InvalidAlgorithmParameterException {
        SharedPreferences preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        String strIv = preferences.getString("biometric-iv", null);
        Cipher cipher = getCipher();
        byte[] iv = Crypto.base64Decrypt(strIv);
        SecretKey secretKey = getSecretKey();
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
        return new BiometricPrompt.CryptoObject(cipher);
    }

    public static byte[] decodeBiometricPassword(Context context, BiometricPrompt.CryptoObject cryptoObject) throws BadPaddingException, IllegalBlockSizeException {
        SharedPreferences preferences = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        String strEncKey = preferences.getString("biometric-key", null);
        byte[] encryptKey = Crypto.base64Decrypt(strEncKey);
        Cipher cipher = cryptoObject.getCipher();
        return cipher.doFinal(encryptKey);
    }

    private static void generateSecretKey(KeyGenParameterSpec keyGenParameterSpec) throws NoSuchProviderException, NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGenerator.init(keyGenParameterSpec);
        keyGenerator.generateKey();
    }

    private static SecretKey getSecretKey() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableKeyException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        return ((SecretKey) keyStore.getKey(KEY_NAME, null));
    }

    private static Cipher getCipher() throws NoSuchPaddingException, NoSuchAlgorithmException {
        return Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7);
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
        if (checkBiometricOpen(context)) {
            disableBiometricPassword(context);
        }
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

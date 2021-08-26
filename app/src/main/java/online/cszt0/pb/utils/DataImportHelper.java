package online.cszt0.pb.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import online.cszt0.pb.BuildConfig;
import online.cszt0.pb.R;
import online.cszt0.pb.bean.PasswordRecord;

public class DataImportHelper {
    private final Context context;
    private final File cacheFolder;

    private String uuid;
    private String verify;
    private byte[] key;

    public DataImportHelper(Context context) {
        this.context = context;
        cacheFolder = context.getDir("import", Context.MODE_PRIVATE);
        if (cacheFolder.exists()) {
            if (cacheFolder.list().length != 0) {
                IOUtils.deleteFile(cacheFolder);
            }
        }
    }

    public ObservableSource<DataImportHelper> userInputPassword() {
        return Observable.create(emitter -> requestInputPassword(() -> {
            emitter.onNext(this);
            emitter.onComplete();
        }, () -> emitter.onError(new NoPasswordException())));
    }

    public void unzip(FileDescriptor fileDescriptor) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(new FileInputStream(fileDescriptor)))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            int len;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                File dst = new File(cacheFolder, entry.getName());
                dst.getParentFile().mkdirs();
                try (FileOutputStream fileOutputStream = new FileOutputStream(dst)) {
                    while ((len = zipInputStream.read(buf)) > 0) {
                        fileOutputStream.write(buf, 0, len);
                    }
                }
            }
        }
    }

    public void parse() throws IOException, JSONException, UnsupportedVersionException {
        JSONObject manifest = new JSONObject(IOUtils.readFully(new File(cacheFolder, "manifest.json")));
        int dataVersion = manifest.getInt("data-version");
        String checksum = manifest.getString("checksum");

        if (dataVersion == 1) {
            JSONObject keyinfo = new JSONObject((IOUtils.readFully(new File(cacheFolder, "keyinfo.json"))));
            this.uuid = keyinfo.getString("uuid");
            this.verify = keyinfo.getString("verify");

            if (IOUtils.checkChecksum(new File(cacheFolder, "data.db"), checksum)) {
                if (BuildConfig.DEBUG) {
                    throw new IOException("Checksum fail");
                }
                throw new IOException();
            }
        } else {
            String platform = manifest.getString("platform");
            String versionName = manifest.getString("app-version-name");
            int versionCode = manifest.getInt("app-version-code");
            throw new UnsupportedVersionException(platform, versionName, versionCode, dataVersion);
        }
    }

    public void requestInputPassword(Runnable success, Runnable fail) {
        requirePasswordInput(null, key -> {
            this.key = key;
            success.run();
        }, fail);
    }

    public RecordReader getData() {
        return new RecordIterator(key, new File(cacheFolder, "data.db"));
    }

    private void requirePasswordInput(String pwd, Consumer<byte[]> consumer, Runnable cancel) {
        //noinspection ResultOfMethodCallIgnored
        @SuppressLint("CheckResult")
        PasswordDialog dialog = PasswordDialog.create(context, inputPwd -> Observable.just(inputPwd)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map(p -> p.length() > 50 ? "" : p)
                .map(Crypto::userInput2AesKey)
                .map(password -> new Pair<>(password, PasswordUtils.checkPassword(password, uuid, verify)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(pair -> {
                    if (pair.second) {
                        consumer.accept(pair.first);
                    } else {
                        requirePasswordInput(inputPwd, consumer, cancel);
                    }
                }), dialog1 -> cancel.run());
        dialog.setTitle(R.string.dialog_password_import);
        dialog.setCheckSafePassword(false);
        if (pwd != null) {
            dialog.setInitPassword(pwd);
            dialog.setPasswordVisible(true);
            dialog.setErrorMessage(context.getString(R.string.text_wrong_password));
        }
        dialog.show();
    }

    public void clean() {
        IOUtils.deleteFile(cacheFolder);
    }

    public interface RecordReader extends Iterator<PasswordRecord>, AutoCloseable {
        @Override
        void close();
    }

    public static class UnsupportedVersionException extends Exception {
        private final String platform;
        private final String versionName;
        private final int versionCode;
        private final int dataVersion;

        public UnsupportedVersionException(String platform, String versionName, int versionCode, int dataVersion) {
            this.platform = platform;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.dataVersion = dataVersion;
        }

        public String getPlatform() {
            return platform;
        }

        public String getVersionName() {
            return versionName;
        }

        public int getVersionCode() {
            return versionCode;
        }

        public int getDataVersion() {
            return dataVersion;
        }
    }

    private static class RecordIterator implements RecordReader {
        private final byte[] key;
        private final SQLiteDatabase database;
        private final Cursor cursor;
        private boolean hasNext;

        private RecordIterator(byte[] key, File file) {
            this.key = key;
            database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            cursor = database.rawQuery("select rowid, title, account, password, detail from book", new String[0]);
            hasNext = cursor.moveToNext();
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public PasswordRecord next() {
            if (!hasNext) {
                throw new NoSuchElementException();
            }
            PasswordRecord record = new PasswordRecord();
            record.setRowid(cursor.getInt(0));
            record.setTitle(Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(1)), key));
            record.setAccount(Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(2)), key));
            record.setPassword(Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(3)), key));
            record.setDetail(Crypto.decryptStringData(Crypto.base64Decrypt(cursor.getString(4)), key));
            hasNext = cursor.moveToNext();
            return record;
        }

        @Override
        public void close() {
            cursor.close();
            database.close();
            hasNext = false;
        }
    }
}

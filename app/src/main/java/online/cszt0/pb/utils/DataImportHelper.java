package online.cszt0.pb.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import online.cszt0.pb.BuildConfig;
import online.cszt0.pb.R;
import online.cszt0.pb.bean.Note;
import online.cszt0.pb.bean.PasswordRecord;

public class DataImportHelper {
    private final Context context;
    private final File cacheFolder;
    private Provider provider;

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

        provider = getProvider(dataVersion);
        if (provider == null) {
            String platform = manifest.getString("platform");
            String versionName = manifest.getString("app-version-name");
            int versionCode = manifest.getInt("app-version-code");
            throw new UnsupportedVersionException(platform, versionName, versionCode, dataVersion);
        }
        provider.cacheFolder = cacheFolder;
        provider.parse(cacheFolder, manifest);
    }

    @Nullable
    private Provider getProvider(int dataVersion) {
        switch (dataVersion) {
            case 1:
                return new V1Provider();
            case 2:
                return new V2Provider();
            default:
                return null;
        }
    }

    public void requestInputPassword(Runnable success, Runnable fail) {
        requirePasswordInput(null, key -> {
            provider.key = key;
            success.run();
        }, fail);
    }

    public Provider getProvider() {
        return provider;
    }

    private void requirePasswordInput(String pwd, Consumer<byte[]> consumer, Runnable cancel) {
        //noinspection ResultOfMethodCallIgnored
        @SuppressLint("CheckResult")
        PasswordDialog dialog = PasswordDialog.create(context, inputPwd -> Observable.just(inputPwd)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map(p -> p.length() > 50 ? "" : p)
                .map(Crypto::userInput2AesKey)
                .map(password -> new Pair<>(password, provider.checkPassword(password)))
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
        Optional.ofNullable(provider).ifPresent(Provider::close);
    }

    public interface BeanReader<Bean> extends Iterator<Bean>, AutoCloseable {
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

    /**
     * 各版本数据包解析器接口
     */
    public abstract static class Provider {
        private File cacheFolder;
        private byte[] key;

        protected abstract void parse(File cacheFolder, JSONObject manifest) throws IOException, JSONException;

        protected abstract boolean checkPassword(byte[] key);

        public BeanReader<PasswordRecord> openPasswordReader() {
            return EmptyReader.getInstance();
        }

        public BeanReader<Note> openNoteReader() {
            return EmptyReader.getInstance();
        }

        public void close() {
        }

        protected File getCacheFolder() {
            return cacheFolder;
        }

        protected byte[] getKey() {
            return key;
        }
    }

    private static class EmptyReader<Bean> implements BeanReader<Bean> {

        private static final EmptyReader<?> instance = new EmptyReader<>();

        private EmptyReader() {
        }

        public static <T> EmptyReader<T> getInstance() {
            //noinspection unchecked
            return (EmptyReader<T>) instance;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Bean next() {
            throw new NoSuchElementException();
        }
    }

    private abstract static class WrapReader<Bean> implements BeanReader<Bean> {
        private final Cursor cursor;
        private boolean hasNext;

        protected WrapReader(Cursor cursor) {
            this.cursor = cursor;
            hasNext = cursor.moveToNext();
        }


        @Override
        public void close() {
            cursor.close();
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public Bean next() {
            if (!hasNext) {
                throw new NoSuchElementException();
            }
            Bean bean = readBean(cursor);
            hasNext = cursor.moveToNext();
            return bean;
        }

        @NonNull
        protected abstract Bean readBean(Cursor cursor);
    }

    private static class V1Provider extends Provider {
        private String uuid;
        private String verify;
        private SQLiteDatabase database;

        @Override
        public void parse(File cacheFolder, JSONObject manifest) throws IOException, JSONException {
            String checksum = manifest.getString("checksum");

            JSONObject keyinfo = new JSONObject((IOUtils.readFully(new File(cacheFolder, "keyinfo.json"))));
            this.uuid = keyinfo.getString("uuid");
            this.verify = keyinfo.getString("verify");

            if (IOUtils.checkChecksum(new File(cacheFolder, "data.db"), checksum)) {
                if (BuildConfig.DEBUG) {
                    throw new IOException("Checksum fail");
                }
                throw new IOException();
            }
        }

        @Override
        public boolean checkPassword(byte[] key) {
            return PasswordUtils.checkPassword(key, uuid, verify);
        }

        @Override
        public BeanReader<PasswordRecord> openPasswordReader() {
            SQLiteDatabase database = openDatabase();
            return new WrapReader<PasswordRecord>(database.rawQuery("select rowid, title, account, password, detail from book", new String[0])) {
                @NonNull
                @Override
                protected PasswordRecord readBean(Cursor cursor) {
                    PasswordRecord passwordRecord = new PasswordRecord();
                    passwordRecord.setRowid(cursor.getInt(0));
                    passwordRecord.setTitle(Crypto.decryptData(cursor.getString(1), getKey()));
                    passwordRecord.setAccount(Crypto.decryptData(cursor.getString(2), getKey()));
                    passwordRecord.setPassword(Crypto.decryptData(cursor.getString(3), getKey()));
                    passwordRecord.setDetail(Crypto.decryptData(cursor.getString(4), getKey()));
                    return passwordRecord;
                }
            };
        }

        protected SQLiteDatabase openDatabase() {
            if (database == null) {
                database = SQLiteDatabase.openDatabase(new File(getCacheFolder(), "data.db").getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            }
            return database;
        }

        @Override
        public void close() {
            database.close();
        }
    }

    private static class V2Provider extends V1Provider {
        @Override
        public BeanReader<Note> openNoteReader() {
            SQLiteDatabase database = openDatabase();
            return new WrapReader<Note>(database.rawQuery("select rowid, title, content from notes", new String[0])) {
                @NonNull
                @Override
                protected Note readBean(Cursor cursor) {
                    Note note = new Note();
                    note.setRowid(cursor.getInt(0));
                    note.setTitle(Crypto.decryptData(cursor.getString(1), getKey()));
                    note.setContent(Crypto.decryptData(cursor.getString(2), getKey()));
                    return note;
                }
            };
        }
    }
}

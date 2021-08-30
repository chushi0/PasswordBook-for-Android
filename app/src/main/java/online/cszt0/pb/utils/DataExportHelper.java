package online.cszt0.pb.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import online.cszt0.pb.BuildConfig;
import online.cszt0.pb.R;

public class DataExportHelper {
    private static final int DATA_VERSION = 2;

    private final Context context;
    private final FileDescriptor fileDescriptor;
    private byte[] key;

    public DataExportHelper(Context context, FileDescriptor fileDescriptor) {
        this.context = context;
        this.fileDescriptor = fileDescriptor;
    }

    public <T> ObservableSource<T> userInputPassword(T t) {
        return Observable.create(emitter -> {
            PasswordDialog dialog = PasswordDialog.create(context, password -> {
                key = Crypto.userInput2AesKey(password);
                emitter.onNext(t);
                emitter.onComplete();
            }, cancel -> emitter.onError(new NoPasswordException()));

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (!preferences.getBoolean("ui_weak_export_password_hint", true)) {
                dialog.setCheckSafePassword(false);
            }

            dialog.setTitle(R.string.dialog_password_export);
            dialog.show();
        });
    }

    public void exportData(byte[] mainKey) throws IOException, JSONException, NoSuchAlgorithmException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(fileDescriptor))) {
            // key info
            out.putNextEntry(new ZipEntry("keyinfo.json"));
            Pair<String, String> keyEncode = PasswordUtils.encodePassword(key);
            JSONObject keyinfo = new JSONObject();
            keyinfo.put("uuid", keyEncode.first);
            keyinfo.put("verify", keyEncode.second);
            out.write(keyinfo.toString().getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            // database
            out.putNextEntry(new ZipEntry("data.db"));
            File tempDatabaseFile = new File(context.getCacheDir(), "export-pb.db");
            if (tempDatabaseFile.exists()) {
                tempDatabaseFile.delete();
            }
            SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(tempDatabaseFile, null);
            Database.dumpTo(context, database, mainKey, key);
            database.close();
            MessageDigest databaseDigest = MessageDigest.getInstance("MD5");
            try (FileInputStream fileInputStream = new FileInputStream(tempDatabaseFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fileInputStream.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                    databaseDigest.update(buffer, 0, len);
                }
            }
            SQLiteDatabase.deleteDatabase(tempDatabaseFile);
            out.closeEntry();
            // manifest
            out.putNextEntry(new ZipEntry("manifest.json"));
            JSONObject manifest = new JSONObject();
            manifest.put("platform", "android");
            manifest.put("app-version-code", BuildConfig.VERSION_CODE);
            manifest.put("app-version-name", BuildConfig.VERSION_NAME);
            manifest.put("data-version", DATA_VERSION);
            manifest.put("checksum", Crypto.base64Encrypt(databaseDigest.digest()));
            out.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}

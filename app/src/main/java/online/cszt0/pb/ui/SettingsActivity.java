package online.cszt0.pb.ui;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Pair;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.json.JSONException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import online.cszt0.pb.BuildConfig;
import online.cszt0.pb.R;
import online.cszt0.pb.utils.Crypto;
import online.cszt0.pb.utils.DataExportHelper;
import online.cszt0.pb.utils.DataImportHelper;
import online.cszt0.pb.utils.Database;
import online.cszt0.pb.utils.MainPassword;
import online.cszt0.pb.utils.NoPasswordException;
import online.cszt0.pb.utils.PasswordDialog;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_EXPORT_DATA = 0;
    private static final int REQUEST_IMPORT_DATA = 1;

    private static final long DIALOG_AFTER = 100;

    private boolean modify = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        setResult(modify ? RESULT_OK : RESULT_CANCELED);
        super.finish();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.activity_settings, rootKey);
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            switch (preference.getKey()) {
                case "secure_auto_clear": {
                    MainPassword.getInstance().postClearUserKey();
                    break;
                }
                case "secure_clear": {
                    MainPassword.getInstance().clearUserKey();
                    Toast.makeText(getContext(), R.string.toast_activity_settings_memory_password_wiped, Toast.LENGTH_SHORT).show();
                    break;
                }
                case "secure_alter_password": {
                    PasswordDialog passwordDialog = PasswordDialog.create(getContext(), this::alterPassword, null);
                    passwordDialog.setTitle(R.string.dialog_alter_password_title);
                    passwordDialog.show();
                    break;
                }
                case "data_export": {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_TITLE, getContext().getString(R.string.file_data_export));
                    startActivityForResult(intent, REQUEST_EXPORT_DATA);
                    break;
                }
                case "data_import": {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    startActivityForResult(intent, REQUEST_IMPORT_DATA);
                    break;
                }
                case "data_clear": {
                    SpannableStringBuilder title = new SpannableStringBuilder(getString(R.string.dialog_wipe_data_title));
                    title.setSpan(new ForegroundColorSpan(Color.RED), 0, title.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                    SpannableStringBuilder delete = new SpannableStringBuilder(getString(R.string.dialog_wipe_data_positive));
                    delete.setSpan(new ForegroundColorSpan(Color.RED), 0, delete.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                    new AlertDialog.Builder(getContext())
                            .setTitle(title)
                            .setMessage(R.string.dialog_wipe_data_message)
                            .setPositiveButton(delete, (dialog, which) -> wipeData())
                            .setNegativeButton(R.string.dialog_common_cancel, null)
                            .show();
                    break;
                }
            }
            return super.onPreferenceTreeClick(preference);
        }

        private ProgressDialog getProgressDialog(@StringRes int message) {
            ProgressDialog dialog = new ProgressDialog(getContext());
            dialog.setMessage(getString(message));
            dialog.setCancelable(false);
            return dialog;
        }

        @SuppressLint("CheckResult")
        private void wipeData() {
            ProgressDialog dialog = getProgressDialog(R.string.processing_wipe_data);
            Handler handler = new Handler();
            MainPassword.getInstance().forceInputPassword(getContext())
                    .observeOn(Schedulers.io())
                    .doOnNext(key -> handler.postDelayed(dialog::show, DIALOG_AFTER))
                    .doOnNext(key -> Database.wipeAll(getContext()))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(key -> {
                        handler.removeCallbacksAndMessages(null);
                        dialog.dismiss();
                        MainPassword.getInstance().clearUserKey();
                        Toast.makeText(getContext(), R.string.toast_activity_settings_data_wiped, Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(getContext(), MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        getActivity().finish();
                    }, e -> {
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                        handler.removeCallbacksAndMessages(null);
                        dialog.dismiss();
                        String cause;

                        if (e instanceof MainPassword.NoUserKeyException) {
                            cause = getString(R.string.fail_no_main_password);
                        } else {
                            cause = getString(R.string.fail_unknown);
                        }
                        Toast.makeText(getContext(), getString(R.string.toast_activity_settings_wipe_data_fail, cause), Toast.LENGTH_SHORT).show();
                    });
        }

        @SuppressLint("CheckResult")
        private void alterPassword(String password) {
            ProgressDialog dialog = getProgressDialog(R.string.processing_alter_password);
            Handler handler = new Handler();
            MainPassword.getInstance().userKeyObservable(getContext())
                    .observeOn(Schedulers.io())
                    .doOnNext(m -> handler.postDelayed(dialog::show, DIALOG_AFTER))
                    .map(oldKey -> {
                        byte[] newKey = Crypto.userInput2AesKey(password);
                        Database.resaveAll(getContext(), oldKey, newKey);
                        return newKey;
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(key -> {
                        handler.removeCallbacksAndMessages(null);
                        dialog.dismiss();
                        MainPassword.getInstance().setUserKey(key);
                        Toast.makeText(getContext(), R.string.toast_activity_settings_password_altered, Toast.LENGTH_SHORT).show();
                    }, e -> {
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                        handler.removeCallbacksAndMessages(null);
                        dialog.dismiss();
                        String cause;
                        if (e instanceof MainPassword.NoUserKeyException) {
                            cause = getString(R.string.fail_no_main_password);
                        } else {
                            cause = getString(R.string.fail_unknown);
                        }
                        Toast.makeText(getContext(), getString(R.string.toast_activity_settings_alter_password_fail, cause), Toast.LENGTH_SHORT).show();
                    });
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            if (resultCode != RESULT_OK) {
                return;
            }
            switch (requestCode) {
                case REQUEST_EXPORT_DATA: {
                    Optional.ofNullable(data)
                            .map(Intent::getData)
                            .ifPresent(this::exportData);
                    break;
                }
                case REQUEST_IMPORT_DATA: {
                    Optional.ofNullable(data)
                            .map(Intent::getData)
                            .ifPresent(this::importData);
                    break;
                }
            }
            super.onActivityResult(requestCode, resultCode, data);
        }

        @SuppressLint("CheckResult")
        private void importData(Uri uri) {
            ProgressDialog loading = getProgressDialog(R.string.processing_analyse_data);
            ProgressDialog importing = getProgressDialog(R.string.processing_import);
            Handler handler = new Handler();
            Observable.just(uri)
                    .observeOn(Schedulers.io())
                    // 解析 Uri
                    .map(u -> Optional.ofNullable(getActivity().getContentResolver().openFileDescriptor(u, "r")))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(ParcelFileDescriptor::getFileDescriptor)
                    // 显示解析对话框
                    .doOnNext(e -> handler.postDelayed(loading::show, DIALOG_AFTER))
                    // 解压并解析数据包
                    .map(fd -> {
                        DataImportHelper dataImportHelper = new DataImportHelper(getContext());
                        dataImportHelper.unzip(fd);
                        return dataImportHelper;
                    })
                    .doOnNext(DataImportHelper::parse)
                    // 隐藏解析对话框
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext(e -> {
                        handler.removeCallbacksAndMessages(null);
                        loading.dismiss();
                    })
                    // 要求用户输入数据包密码
                    .flatMap(DataImportHelper::userInputPassword)
                    // 获取当前主密码
                    .flatMap(dataImportHelper -> MainPassword.getInstance().userKeyObservableSource(getContext(), key -> new Pair<>(dataImportHelper, key)))
                    // 显示导入对话框
                    .observeOn(Schedulers.io())
                    .doOnNext(e -> handler.postDelayed(importing::show, DIALOG_AFTER))
                    // 导入数据
                    .doOnNext(pair -> Database.importData(getContext(), pair.first, pair.second))
                    // 清理缓存文件
                    .doOnNext(e -> e.first.clean())
                    // 完成
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(e -> {
                        handler.removeCallbacksAndMessages(null);
                        importing.dismiss();
                        ((SettingsActivity) getActivity()).modify = true;
                        Toast.makeText(getContext(), R.string.toast_activity_settings_imported, Toast.LENGTH_SHORT).show();
                    }, e -> {
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                        handler.removeCallbacksAndMessages(null);
                        loading.dismiss();
                        importing.dismiss();
                        String cause;
                        if (e instanceof IOException || e instanceof JSONException) {
                            cause = getString(R.string.fail_bad_datapack);
                        } else if (e instanceof MainPassword.NoUserKeyException) {
                            cause = getString(R.string.fail_no_main_password);
                        } else if (e instanceof NoPasswordException) {
                            cause = getString(R.string.fail_no_datapack_password);
                        } else if (e instanceof DataImportHelper.UnsupportedVersionException) {
                            DataImportHelper.UnsupportedVersionException uve = (DataImportHelper.UnsupportedVersionException) e;
                            cause = getString(R.string.fail_unsupport_datapack, uve.getPlatform(), uve.getVersionName());
                        } else {
                            cause = getString(R.string.fail_unknown);
                        }
                        Toast.makeText(getContext(), getString(R.string.toast_activity_settings_import_fail, cause), Toast.LENGTH_SHORT).show();
                    });
        }

        @SuppressLint("CheckResult")
        private void exportData(Uri uri) {
            ProgressDialog exporting = getProgressDialog(R.string.processing_export);
            Handler handler = new Handler();
            Observable.just(uri)
                    .observeOn(Schedulers.io())
                    // 解析 Uri
                    .map(u -> Optional.ofNullable(getActivity().getContentResolver().openFileDescriptor(u, "w")))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(ParcelFileDescriptor::getFileDescriptor)
                    .map(fd -> new DataExportHelper(getContext(), fd))
                    // 要求用户输入数据包密码
                    .observeOn(AndroidSchedulers.mainThread())
                    .flatMap(dataExportHelper -> dataExportHelper.userInputPassword(dataExportHelper))
                    // 获取当前主密码
                    .flatMap(dataExportHelper -> MainPassword.getInstance().userKeyObservableSource(getContext(), key -> new Pair<>(dataExportHelper, key)))
                    // 显示对话框
                    .observeOn(Schedulers.io())
                    .doOnNext(pair -> handler.postDelayed(exporting::show, DIALOG_AFTER))
                    // 导出
                    .doOnNext(pair -> pair.first.exportData(pair.second))
                    // 完成
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(e -> {
                        handler.removeCallbacksAndMessages(null);
                        exporting.dismiss();
                        Toast.makeText(getContext(), R.string.toast_activity_settings_exported, Toast.LENGTH_SHORT).show();
                    }, e -> {
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                        handler.removeCallbacksAndMessages(null);
                        exporting.dismiss();
                        String cause;
                        if (e instanceof MainPassword.NoUserKeyException) {
                            cause = getString(R.string.fail_no_main_password);
                        } else if (e instanceof NoPasswordException) {
                            cause = getString(R.string.fail_no_datapack_password);
                        } else if (e instanceof IOException) {
                            cause = getString(R.string.fail_io_write);
                        } else {
                            cause = getString(R.string.fail_unknown);
                        }
                        Toast.makeText(getContext(), getString(R.string.toast_activity_settings_export_fail, cause), Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
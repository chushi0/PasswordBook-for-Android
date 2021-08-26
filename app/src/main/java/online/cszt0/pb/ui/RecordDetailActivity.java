package online.cszt0.pb.ui;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import online.cszt0.pb.BuildConfig;
import online.cszt0.pb.R;
import online.cszt0.pb.bean.PasswordRecord;
import online.cszt0.pb.utils.Database;
import online.cszt0.pb.utils.DialogProcess;
import online.cszt0.pb.utils.MainPassword;
import online.cszt0.pb.utils.PasswordUtils;
import online.cszt0.pb.view.PasswordViewDelegate;

public class RecordDetailActivity extends AppCompatActivity {

    static final String ACTION_CREATE_NEW = "online.cszt0.pb.action.CREATE_NEW";
    static final String ACTION_MODIFY = "online.cszt0.pb.action.MODIFY";

    static final String EXTRA_KEY_RECORD = "record";

    private PasswordViewDelegate delegate;
    private EditText title;
    private EditText account;
    private EditText detail;

    private PasswordRecord record;

    private boolean editMode;
    private MenuItem modeItem;

    private int result = RESULT_CANCELED;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_detail);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        delegate = new PasswordViewDelegate(findViewById(R.id.input_password), findViewById(R.id.eye), findViewById(R.id.clear));
        title = findViewById(R.id.input_title);
        account = findViewById(R.id.input_account);
        detail = findViewById(R.id.input_detail);
        findViewById(R.id.btn_copy_password).setOnClickListener(this::copyPassword);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent intent = getIntent();
        switch (Optional.ofNullable(intent.getAction()).orElse(ACTION_CREATE_NEW)) {
            case ACTION_CREATE_NEW: {
                setEditMode(true);
                record = new PasswordRecord();
                break;
            }
            case ACTION_MODIFY: {
                setEditMode(false);
                record = intent.getParcelableExtra(EXTRA_KEY_RECORD);
                if (BuildConfig.DEBUG && record == null) {
                    throw new NullPointerException();
                }
                title.setText(record.getTitle());
                MainPassword.getInstance().userKeyObservable(this)
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .map(k -> Database.readDetail(this, record, k))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(r -> {
                            if (r) {
                                decryptRecordFinish();
                            } else {
                                decryptRecordError(new IOException());
                            }
                        }, this::decryptRecordError);
            }
            break;
        }
    }

    private void copyPassword(View view) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Password", delegate.getPassword()));
        Toast.makeText(this, R.string.toast_activity_detail_password_copied, Toast.LENGTH_SHORT).show();
    }

    private void decryptRecordFinish() {
        title.setText(record.getTitle());
        account.setText(record.getAccount());
        detail.setText(record.getDetail());
        delegate.setPassword(record.getPassword());
    }

    private void decryptRecordError(Throwable e) {
        String cause;
        if (e instanceof MainPassword.NoUserKeyException) {
            cause = getString(R.string.fail_no_main_password);
        } else {
            cause = getString(R.string.fail_unknown);
        }
        Toast.makeText(this, getString(R.string.toast_activity_detail_load_fail, cause), Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (record.getRowid() == 0) {
            getMenuInflater().inflate(R.menu.activity_record_detail_new, menu);
        } else {
            getMenuInflater().inflate(R.menu.activity_record_detail_modify, menu);
        }
        modeItem = menu.findItem(R.id.mode);
        updateModeMenu();
        return super.onCreateOptionsMenu(menu);
    }

    private void setEditMode(boolean editMode) {
        this.editMode = editMode;
        delegate.setEditable(editMode);
        if (editMode) {
            title.setFocusable(true);
            title.setFocusableInTouchMode(true);
            account.setFocusable(true);
            account.setFocusableInTouchMode(true);
            detail.setFocusable(true);
            detail.setFocusableInTouchMode(true);
        } else {
            title.setFocusable(false);
            title.setFocusableInTouchMode(false);
            account.setFocusable(false);
            account.setFocusableInTouchMode(false);
            detail.setFocusable(false);
            detail.setFocusableInTouchMode(false);

            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(title.getWindowToken(), 0);
            inputMethodManager.hideSoftInputFromWindow(account.getWindowToken(), 0);
            inputMethodManager.hideSoftInputFromWindow(detail.getWindowToken(), 0);
            inputMethodManager.hideSoftInputFromWindow(delegate.getWindowToken(), 0);
        }
        updateModeMenu();
    }

    private void updateModeMenu() {
        Optional.ofNullable(modeItem).ifPresent(menuItem -> {
            if (editMode) {
                modeItem.setTitle(R.string.menu_edit_mode_edit);
                modeItem.setIcon(R.drawable.ic_baseline_edit_24);
            } else {
                modeItem.setTitle(R.string.menu_edit_mode_view);
                modeItem.setIcon(R.drawable.ic_baseline_eye_24);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.delete) {
            deleteRecord();
        } else if (itemId == R.id.commit) {
            commitRecord();
        } else if (itemId == R.id.mode) {
            setEditMode(!editMode);
        } else if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("CheckResult")
    private void commitRecord() {
        delegate.setPasswordVisible(false);
        String password = delegate.getPassword();
        int weak = PasswordUtils.isWeakPassword(password);

        DialogProcess.create(this)
                .abort(password::isEmpty, R.string.dialog_password_no_password_title, R.string.dialog_password_no_password_message)
                .abort(() -> (title.getText().toString().isEmpty()), R.string.dialog_password_no_title_title, R.string.dialog_password_no_title_message)
                .question(() -> (weak & PasswordUtils.WEAK_TOO_SHORT) != 0, R.string.dialog_password_too_short_title, R.string.dialog_password_too_short_message, R.string.dialog_password_use, R.string.dialog_password_change_password)
                .question(() -> (weak & PasswordUtils.WEAK_FEW_TYPE) != 0, R.string.dialog_password_few_type_title, R.string.dialog_password_few_type_message, R.string.dialog_password_use, R.string.dialog_password_change_password)
                .question(() -> (weak & PasswordUtils.WEAK_NOT_ASCII) != 0, R.string.dialog_password_not_ascii_title, R.string.dialog_password_not_ascii_message, R.string.dialog_password_use, R.string.dialog_password_change_password)
                .question(() -> title.getText().toString().length() > 20, R.string.dialog_password_too_long_title_title, R.string.dialog_password_too_long_title_message, R.string.dialog_password_use, R.string.dialog_password_change_title)
                .then(() -> MainPassword.getInstance().userKeyObservable(this)
                        .observeOn(Schedulers.io())
                        .doOnNext(key -> {
                            record.setTitle(title.getText().toString());
                            record.setAccount(account.getText().toString());
                            record.setPassword(password);
                            record.setDetail(detail.getText().toString());
                            Database.saveDetail(this, record, key);
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(a -> {
                            Toast.makeText(this, R.string.toast_activity_detail_saved, Toast.LENGTH_SHORT).show();
                            setEditMode(false);
                            invalidateOptionsMenu();
                            result = RESULT_OK;
                        }, e -> {
                            if (BuildConfig.DEBUG) {
                                e.printStackTrace();
                            }
                            String cause;
                            if (e instanceof MainPassword.NoUserKeyException) {
                                cause = getString(R.string.fail_no_main_password);
                            } else {
                                cause = getString(R.string.fail_unknown);
                            }
                            Toast.makeText(this, getString(R.string.toast_activity_detail_save_fail, cause), Toast.LENGTH_SHORT).show();
                        }))
                .start();
    }

    @SuppressLint("CheckResult")
    private void deleteRecord() {
        DialogProcess.create(this)
                .question(() -> true, R.string.dialog_record_delete_title, R.string.dialog_record_delete_message, R.string.dialog_record_delete_ok, R.string.dialog_common_cancel)
                .then(() -> {
                    //noinspection ResultOfMethodCallIgnored
                    MainPassword.getInstance().userKeyObservable(this)
                            .observeOn(Schedulers.io())
                            .doOnNext(key -> Database.removeRecord(this, record))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(a -> {
                                result = RESULT_OK;
                                Toast.makeText(this, R.string.toast_activity_detail_deleted, Toast.LENGTH_SHORT).show();
                                finish();
                            }, e -> {
                                if (BuildConfig.DEBUG) {
                                    e.printStackTrace();
                                }
                                String cause;
                                if (e instanceof MainPassword.NoUserKeyException) {
                                    cause = getString(R.string.fail_no_main_password);
                                } else {
                                    cause = getString(R.string.fail_unknown);
                                }
                                Toast.makeText(this, getString(R.string.toast_activity_detail_delete_fail, cause), Toast.LENGTH_SHORT).show();
                            });
                })
                .start();
    }

    @Override
    public void onBackPressed() {
        if (!recordNotSave()) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_save_before_exit_title)
                .setMessage(R.string.dialog_save_before_exit_message)
                .setPositiveButton(R.string.dialog_save_before_exit_positive, (dialog, which) -> commitRecord())
                .setNegativeButton(R.string.dialog_save_before_exit_negative, (dialog, which) -> super.onBackPressed())
                .setNeutralButton(R.string.dialog_common_cancel, null)
                .show();
    }

    private boolean recordNotSave() {
        String title = this.title.getText().toString();
        String account = this.account.getText().toString();
        String password = delegate.getPassword();
        String detail = this.detail.getText().toString();

        if (record.getRowid() == 0 && title.isEmpty() && account.isEmpty() && password.isEmpty() && detail.isEmpty()) {
            return false;
        }

        return record.getRowid() == 0 ||
                !Objects.equals(record.getTitle(), title) ||
                !Objects.equals(Optional.ofNullable(record.getAccount()).orElse(""), account) ||
                !Objects.equals(Optional.ofNullable(record.getPassword()).orElse(""), password) ||
                !Objects.equals(Optional.ofNullable(record.getDetail()).orElse(""), detail);
    }

    @Override
    public void finish() {
        setResult(result);
        super.finish();
    }
}
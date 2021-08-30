package online.cszt0.pb.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.Objects;
import java.util.Optional;

import online.cszt0.pb.R;
import online.cszt0.pb.bean.PasswordRecord;
import online.cszt0.pb.utils.Database;
import online.cszt0.pb.utils.DialogProcess;
import online.cszt0.pb.utils.PasswordUtils;
import online.cszt0.pb.view.PasswordViewDelegate;

public class RecordDetailActivity extends AbstractDetailActivity<PasswordRecord> {

    private PasswordViewDelegate delegate;
    private EditText title;
    private EditText account;
    private EditText detail;

    @NonNull
    @Override
    protected PasswordRecord createNewData() {
        return new PasswordRecord();
    }

    @Override
    protected void initializeView() {
        setContentView(R.layout.activity_record_detail);
        delegate = new PasswordViewDelegate(findViewById(R.id.input_password), findViewById(R.id.eye), findViewById(R.id.clear));
        title = findViewById(R.id.input_title);
        account = findViewById(R.id.input_account);
        detail = findViewById(R.id.input_detail);
        findViewById(R.id.btn_copy_password).setOnClickListener(this::copyPassword);
    }

    @Override
    protected boolean asyncReadDetail(@NonNull PasswordRecord record, byte[] key) {
        return Database.readRecord(this, record, key);
    }

    @Override
    protected void asyncSaveDetail(@NonNull PasswordRecord record, byte[] key) {
        String title = this.title.getText().toString();
        String account = this.account.getText().toString();
        String password = delegate.getPassword();
        String detail = this.detail.getText().toString();

        record.setTitle(title);
        record.setAccount(account);
        record.setPassword(password);
        record.setDetail(detail);

        Database.saveRecord(this, record, key);
    }

    @Override
    protected void asyncRemoveDetail(@NonNull PasswordRecord record, byte[] key) {
        Database.removeRecord(this, record);
    }

    @Override
    protected void fillData(@NonNull PasswordRecord record) {
        title.setText(record.getTitle());
        account.setText(record.getAccount());
        detail.setText(record.getDetail());
        delegate.setPassword(record.getPassword());
    }

    @Override
    protected void onEditableChange(boolean editable) {
        title.setFocusable(editable);
        title.setFocusableInTouchMode(editable);
        account.setFocusable(editable);
        account.setFocusableInTouchMode(editable);
        detail.setFocusable(editable);
        detail.setFocusableInTouchMode(editable);
        delegate.setEditable(editable);

        if (!editable) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(title.getWindowToken(), 0);
            inputMethodManager.hideSoftInputFromWindow(account.getWindowToken(), 0);
            inputMethodManager.hideSoftInputFromWindow(delegate.getWindowToken(), 0);
            inputMethodManager.hideSoftInputFromWindow(delegate.getWindowToken(), 0);
        }
    }

    @Override
    protected void fillPartData(@NonNull PasswordRecord record) {
        super.fillPartData(record);
        title.setText(record.getTitle());
        Optional.ofNullable(record.getPassword()).ifPresent(password -> delegate.setPassword(password));
    }

    private void copyPassword(View view) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Password", delegate.getPassword()));
        Toast.makeText(this, R.string.toast_activity_detail_password_copied, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void beforeSaveHook(Runnable next) {
        delegate.setPasswordVisible(false);
        String password = delegate.getPassword();
        int weak = PasswordUtils.isWeakPassword(password);

        DialogProcess.DialogProcessLink processLink = DialogProcess.create(this)
                .abort(password::isEmpty, R.string.dialog_password_no_password_title, R.string.dialog_password_no_password_message)
                .abort(() -> (title.getText().toString().isEmpty()), R.string.dialog_password_no_title_title, R.string.dialog_password_no_title_message);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.getBoolean("ui_weak_password_hint", true)) {
            processLink = processLink.question(() -> (weak & PasswordUtils.WEAK_TOO_SHORT) != 0, R.string.dialog_password_too_short_title, R.string.dialog_password_too_short_message, R.string.dialog_password_use, R.string.dialog_password_change_password)
                    .question(() -> (weak & PasswordUtils.WEAK_FEW_TYPE) != 0, R.string.dialog_password_few_type_title, R.string.dialog_password_few_type_message, R.string.dialog_password_use, R.string.dialog_password_change_password)
                    .question(() -> (weak & PasswordUtils.WEAK_NOT_ASCII) != 0, R.string.dialog_password_not_ascii_title, R.string.dialog_password_not_ascii_message, R.string.dialog_password_use, R.string.dialog_password_change_password)
                    .question(() -> title.getText().toString().length() > 20, R.string.dialog_password_too_long_title_title, R.string.dialog_password_too_long_title_message, R.string.dialog_password_use, R.string.dialog_password_change_title);
        }

        processLink.then(next)
                .start();
    }

    @Override
    protected boolean isDataSaved(PasswordRecord record) {
        String title = this.title.getText().toString();
        String account = this.account.getText().toString();
        String password = delegate.getPassword();
        String detail = this.detail.getText().toString();

        return super.isDataSaved(record) && (
                (record.getRowid() == 0 && title.isEmpty() && account.isEmpty() && password.isEmpty() && detail.isEmpty()) ||
                        (record.getRowid() != 0 &&
                                Objects.equals(Optional.ofNullable(record.getTitle()).orElse(""), title) &&
                                Objects.equals(Optional.ofNullable(record.getAccount()).orElse(""), account) &&
                                Objects.equals(Optional.ofNullable(record.getPassword()).orElse(""), password) &&
                                Objects.equals(Optional.ofNullable(record.getDetail()).orElse(""), detail))
        );
    }
}
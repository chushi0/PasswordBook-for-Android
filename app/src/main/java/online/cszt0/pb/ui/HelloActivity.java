package online.cszt0.pb.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import online.cszt0.pb.R;
import online.cszt0.pb.utils.Crypto;
import online.cszt0.pb.utils.Database;
import online.cszt0.pb.utils.DialogProcess;
import online.cszt0.pb.utils.MainPassword;
import online.cszt0.pb.utils.PasswordUtils;
import online.cszt0.pb.view.PasswordViewDelegate;

public class HelloActivity extends AppCompatActivity {

    private PasswordViewDelegate passwordViewDelegate;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hello);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        passwordViewDelegate = new PasswordViewDelegate(findViewById(R.id.password), findViewById(R.id.eye), findViewById(R.id.clear));
        findViewById(R.id.start).setOnClickListener(this::startClick);
    }

    private void startClick(View view) {
        passwordViewDelegate.setPasswordVisible(false);
        String password = passwordViewDelegate.getPassword();
        int weak = PasswordUtils.isWeakPassword(password);

        DialogProcess.create(this)
                .abort(password::isEmpty, R.string.dialog_password_main_no_password_title, R.string.dialog_password_main_no_password_message)
                .abort(() -> password.length() > 50, R.string.dialog_password_main_too_long_title, R.string.dialog_password_main_too_lang_message)
                .question(() -> (weak & PasswordUtils.WEAK_TOO_SHORT) != 0, R.string.dialog_password_main_too_short_title, R.string.dialog_password_main_too_short_message, R.string.dialog_password_main_use, R.string.dialog_password_main_change)
                .question(() -> (weak & PasswordUtils.WEAK_FEW_TYPE) != 0, R.string.dialog_password_main_few_type_title, R.string.dialog_password_main_few_type_message, R.string.dialog_password_main_use, R.string.dialog_password_main_change)
                .question(() -> (weak & PasswordUtils.WEAK_NOT_ASCII) != 0, R.string.dialog_password_main_not_ascii_title, R.string.dialog_password_main_not_ascii_message, R.string.dialog_password_main_use, R.string.dialog_password_main_change)
                .then(() -> {
                    byte[] key = Crypto.userInput2AesKey(password);
                    Database.initializeDatabase(this, key);
                    MainPassword.getInstance().setUserKey(key);
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                })
                .start();
    }
}

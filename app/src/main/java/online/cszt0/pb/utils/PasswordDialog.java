package online.cszt0.pb.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricPrompt;

import java.util.function.Consumer;

import online.cszt0.pb.R;
import online.cszt0.pb.view.PasswordViewDelegate;

public class PasswordDialog extends AlertDialog {
    private final Context context;

    private PasswordViewDelegate delegate;
    private TextView infoView;
    private PasswordListener passwordListener;
    private OnCancelListener cancelListener;

    private BiometricPrompt.CryptoObject cryptoObject;
    private BiometricPasswordListener biometricPasswordListener;

    private boolean checkSafePassword;
    private String errorMessage;
    private String initPassword;
    private boolean passwordVisible;

    private String biometricTitle;
    private String biometricSubtitle;

    private PasswordDialog(@NonNull Context context) {
        super(context);
        this.context = context;
        checkSafePassword = true;
    }

    public static PasswordDialog create(Context context, PasswordListener listener, OnCancelListener cancelListener) {
        if (cancelListener == null) {
            cancelListener = PasswordDialog::emptyCancel;
        }
        PasswordDialog dialog = new PasswordDialog(context);
        dialog.passwordListener = listener;
        dialog.cancelListener = cancelListener;
        return dialog;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setInitPassword(String initPassword) {
        this.initPassword = initPassword;
    }

    public void setPasswordVisible(boolean passwordVisible) {
        this.passwordVisible = passwordVisible;
    }

    public void setCheckSafePassword(boolean checkSafePassword) {
        this.checkSafePassword = checkSafePassword;
    }

    public void setSupportBiometric(String title, String subtitle, BiometricPrompt.CryptoObject cryptoObject, BiometricPasswordListener listener) {
        this.biometricTitle = title;
        this.biometricSubtitle = subtitle;
        this.cryptoObject = cryptoObject;
        this.biometricPasswordListener = listener;
    }

    private static void emptyCancel(DialogInterface dialogInterface) {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_password_input, null);
        delegate = new PasswordViewDelegate(view.findViewById(R.id.password), view.findViewById(R.id.eye), view.findViewById(R.id.clear));
        infoView = view.findViewById(R.id.info);
        if (errorMessage != null) {
            infoView.setText(errorMessage);
        }
        delegate.setPasswordVisible(passwordVisible);
        if (initPassword != null) {
            delegate.setPassword(initPassword);
        }
        setView(view);
        setButton(BUTTON_POSITIVE, getContext().getText(R.string.dialog_common_ok), this::empty);
        setButton(BUTTON_NEGATIVE, getContext().getText(R.string.dialog_common_cancel), this::onNegative);
        boolean useBiometric = false;
        if (biometricPasswordListener != null && BiometricUtils.isBiometricHardwareSupported(getContext())) {
            setButton(BUTTON_NEUTRAL, getContext().getString(R.string.dialog_password_biometric), this::empty);
            useBiometric = true;
        }
        super.onCreate(savedInstanceState);
        getButton(BUTTON_POSITIVE).setOnClickListener(this::onPositive);
        getButton(BUTTON_NEUTRAL).setOnClickListener(this::onBiometric);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setOnCancelListener(cancelListener);

        if (useBiometric) {
            onBiometric(null);
        }
    }

    private void onPositive(View view) {
        delegate.setPasswordVisible(false);
        String password = delegate.getPassword();
        if (checkSafePassword) {
            int weak = PasswordUtils.isWeakPassword(password);

            DialogProcess.create(getContext())
                    .abort(password::isEmpty, R.string.dialog_password_main_no_password_title, R.string.dialog_password_main_no_password_message)
                    .abort(() -> password.length() > 50, R.string.dialog_password_main_too_long_title, R.string.dialog_password_main_too_lang_message)
                    .question(() -> (weak & PasswordUtils.WEAK_TOO_SHORT) != 0, R.string.dialog_password_main_too_short_title, R.string.dialog_password_main_too_short_message, R.string.dialog_password_main_use, R.string.dialog_password_main_change)
                    .question(() -> (weak & PasswordUtils.WEAK_FEW_TYPE) != 0, R.string.dialog_password_main_few_type_title, R.string.dialog_password_main_few_type_message, R.string.dialog_password_main_use, R.string.dialog_password_main_change)
                    .question(() -> (weak & PasswordUtils.WEAK_NOT_ASCII) != 0, R.string.dialog_password_main_not_ascii_title, R.string.dialog_password_main_not_ascii_message, R.string.dialog_password_main_use, R.string.dialog_password_main_change)
                    .then(() -> {
                        passwordListener.onPassword(password);
                        dismiss();
                    })
                    .start();
        } else {
            passwordListener.onPassword(password);
            dismiss();
        }
    }

    private void onBiometric(View view) {
        BiometricUtils.startBiometricAuthenticate(context, biometricTitle, biometricSubtitle, cryptoObject, cryptoObject -> {
            biometricPasswordListener.onPass(cryptoObject);
            dismiss();
        }, e -> infoView.setText(context.getString(R.string.fail_biometric, e)));
    }

    private void onNegative(DialogInterface dialogInterface, int i) {
        cancelListener.onCancel(dialogInterface);
    }

    private void empty(DialogInterface dialogInterface, int i) {
    }

    public interface PasswordListener {
        void onPassword(String password);
    }

    public interface BiometricPasswordListener {
        void onPass(BiometricPrompt.CryptoObject cryptoObject);
    }
}

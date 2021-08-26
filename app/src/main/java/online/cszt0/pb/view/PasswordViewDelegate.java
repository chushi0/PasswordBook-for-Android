package online.cszt0.pb.view;

import android.os.IBinder;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import online.cszt0.pb.R;

public class PasswordViewDelegate {
    private final EditText passwordView;
    private final ImageView eyeView;
    private final ImageView clearView;
    private boolean visible;
    private boolean editable = true;

    public PasswordViewDelegate(EditText passwordView, ImageView eyeView, ImageView clearView) {
        this.passwordView = passwordView;
        this.eyeView = eyeView;
        this.clearView = clearView;

        passwordView.setOnFocusChangeListener(this::passwordFocusChange);
        eyeView.setOnClickListener(this::eyeClick);
        clearView.setOnClickListener(this::clearClick);

        if (passwordView.hasFocus()) {
            clearView.setVisibility(View.VISIBLE);
            clearView.setEnabled(true);
        } else {
            clearView.setVisibility(View.GONE);
            clearView.setEnabled(false);
        }

        setPasswordVisible(false);
    }

    public void setPasswordVisible(boolean visible) {
        this.visible = visible;
        int selectStart = passwordView.getSelectionStart();
        int selectEnd = passwordView.getSelectionEnd();
        if (visible) {
            eyeView.setImageResource(R.drawable.ic_outline_eye_24);
        } else {
            eyeView.setImageResource(R.drawable.ic_baseline_eye_24);
        }
        if (this.visible) {
            passwordView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        } else {
            passwordView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        passwordView.setSelection(selectStart, selectEnd);
    }

    public void setPassword(String password) {
        passwordView.setText(password);
    }

    private void passwordFocusChange(View view, boolean focus) {
        if (focus) {
            clearView.setVisibility(View.VISIBLE);
            clearView.setEnabled(true);
        } else {
            clearView.setVisibility(View.GONE);
            clearView.setEnabled(false);
            setPasswordVisible(false);
        }
    }

    private void eyeClick(View view) {
        setPasswordVisible(!visible);
    }

    private void clearClick(View view) {
        passwordView.setText(null);
    }

    public String getPassword() {
        return passwordView.getText().toString();
    }

    public void setEditable(boolean editable) {
        if (this.editable == editable) {
            return;
        }
        this.editable = editable;
        if (editable) {
            passwordView.setFocusable(true);
            passwordView.setFocusableInTouchMode(true);
        } else {
            passwordView.setFocusable(false);
            passwordView.setFocusableInTouchMode(false);
        }
    }

    public IBinder getWindowToken() {
        return passwordView.getWindowToken();
    }
}

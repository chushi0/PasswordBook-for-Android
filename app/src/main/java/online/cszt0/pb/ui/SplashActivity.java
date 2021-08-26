package online.cszt0.pb.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.Nullable;

import online.cszt0.pb.utils.Database;

public class SplashActivity extends Activity {

    private final Handler handler = new Handler(this::handleMessage);

    private static final int WHAT_START_MAIN = 1;
    private static final int WHAT_START_HELLO = 2;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Database.isInitialize(this)) {
            handler.sendEmptyMessage(WHAT_START_MAIN);
        } else {
            handler.sendEmptyMessage(WHAT_START_HELLO);
        }
    }

    private boolean handleMessage(Message message) {
        switch (message.what) {
            case WHAT_START_MAIN:
                startActivity(new Intent(this, MainActivity.class));
                finish();
                break;
            case WHAT_START_HELLO:
                startActivity(new Intent(this, HelloActivity.class));
                finish();
                break;
        }
        return true;
    }
}

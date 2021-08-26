package online.cszt0.pb;

import android.app.Application;

import online.cszt0.pb.utils.MainPassword;

public final class PBApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MainPassword.getInstance().setApplicationContext(getApplicationContext());
    }
}

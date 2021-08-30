package online.cszt0.pb.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import online.cszt0.pb.R;
import online.cszt0.pb.utils.Database;

public class UpgradeActivity extends AppCompatActivity {

    private boolean taskStart;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upgrade);
        if (!taskStart) {
            taskStart = true;
            startTask();
        }
    }

    @SuppressLint("CheckResult")
    private void startTask() {
        //noinspection ResultOfMethodCallIgnored
        Observable.just(this)
                .observeOn(Schedulers.io())
                .doOnNext(e -> Database.upgrade(this))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(next -> {
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
    }

    @Override
    public void onBackPressed() {
    }
}

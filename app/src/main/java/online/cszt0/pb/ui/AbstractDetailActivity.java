package online.cszt0.pb.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.Optional;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import online.cszt0.pb.BuildConfig;
import online.cszt0.pb.R;
import online.cszt0.pb.bean.SQLiteBean;
import online.cszt0.pb.utils.DialogProcess;
import online.cszt0.pb.utils.MainPassword;

public abstract class AbstractDetailActivity<Data extends SQLiteBean> extends AppCompatActivity {
    public static final String ACTION_CREATE_NEW = "online.cszt0.pb.action.CREATE_NEW";
    public static final String ACTION_CREATE_BY = "online.cszt0.pb.action.CREATE_BY";
    public static final String ACTION_MODIFY = "online.cszt0.pb.action.MODIFY";
    public static final String EXTRA_KEY_DATA = "data";

    private Data data;

    private boolean editable;
    private MenuItem modeItem;
    private boolean lastSaveFail;

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeView();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent intent = getIntent();
        switch (Optional.ofNullable(intent.getAction()).orElse(ACTION_CREATE_NEW)) {
            case ACTION_CREATE_NEW: {
                setEditable(true);
                data = createNewData();
                break;
            }
            case ACTION_CREATE_BY: {
                setEditable(true);
                data = intent.getParcelableExtra(EXTRA_KEY_DATA);
                if (BuildConfig.DEBUG && data == null) {
                    throw new NullPointerException();
                }
                fillPartData(data);
                break;
            }
            case ACTION_MODIFY: {
                setEditable(false);
                data = intent.getParcelableExtra(EXTRA_KEY_DATA);
                if (BuildConfig.DEBUG && data == null) {
                    throw new NullPointerException();
                }
                fillPartData(data);
                //noinspection ResultOfMethodCallIgnored
                MainPassword.getInstance().userKeyObservable(this)
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.io())
                        .map(k -> asyncReadDetail(data, k))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(r -> {
                            if (r) {
                                readDataFinish();
                            } else {
                                readDataError(new IOException());
                            }
                        }, this::readDataError);
            }
        }
    }

    private void readDataFinish() {
        fillData(data);
    }

    private void readDataError(Throwable e) {
        String cause = getCause(e, From.READ);
        Toast.makeText(this, getString(R.string.toast_activity_detail_load_fail, cause), Toast.LENGTH_SHORT).show();
    }

    private void setEditable(boolean editable) {
        this.editable = editable;
        onEditableChange(editable);
        updateModeMenu();
    }

    private void updateModeMenu() {
        Optional.ofNullable(modeItem).ifPresent(menuItem -> {
            if (editable) {
                menuItem.setTitle(R.string.menu_edit_mode_edit);
                menuItem.setIcon(R.drawable.ic_baseline_edit_24);
            } else {
                menuItem.setTitle(R.string.menu_edit_mode_view);
                menuItem.setIcon(R.drawable.ic_baseline_eye_24);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (data.getRowid() == 0) {
            getMenuInflater().inflate(R.menu.activity_detail_new, menu);
        } else {
            getMenuInflater().inflate(R.menu.activity_detail_modify, menu);
        }
        modeItem = menu.findItem(R.id.mode);
        updateModeMenu();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.delete) {
            deleteRecord();
        } else if (itemId == R.id.commit) {
            commitRecord(null);
        } else if (itemId == R.id.mode) {
            setEditable(!editable);
        } else if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("CheckResult")
    private void deleteRecord() {
        DialogProcess.create(this)
                .question(() -> true, R.string.dialog_record_delete_title, R.string.dialog_record_delete_message, R.string.dialog_record_delete_ok, R.string.dialog_common_cancel)
                .then(() -> {
                    //noinspection ResultOfMethodCallIgnored
                    MainPassword.getInstance().userKeyObservable(this)
                            .observeOn(Schedulers.io())
                            .doOnNext(key -> asyncRemoveDetail(data, key))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(a -> {
                                setResult(RESULT_OK);
                                Toast.makeText(this, R.string.toast_activity_detail_deleted, Toast.LENGTH_SHORT).show();
                                finish();
                            }, e -> {
                                if (BuildConfig.DEBUG) {
                                    e.printStackTrace();
                                }
                                String cause = getCause(e, From.REMOVE);
                                Toast.makeText(this, getString(R.string.toast_activity_detail_delete_fail, cause), Toast.LENGTH_SHORT).show();
                            });
                })
                .start();
    }

    @SuppressLint("CheckResult")
    private void commitRecord(@Nullable Runnable doNext) {
        beforeSaveHook(() -> {
            //noinspection ResultOfMethodCallIgnored
            MainPassword.getInstance().userKeyObservable(this)
                    .observeOn(Schedulers.io())
                    .doOnNext(key -> asyncSaveDetail(data, key))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(a -> {
                        Toast.makeText(this, R.string.toast_activity_detail_saved, Toast.LENGTH_SHORT).show();
                        setEditable(false);
                        invalidateOptionsMenu();
                        setResult(RESULT_OK);
                        lastSaveFail = false;
                        Optional.ofNullable(doNext).ifPresent(Runnable::run);
                    }, e -> {
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                        lastSaveFail = true;
                        String cause = getCause(e, From.SAVE);
                        Toast.makeText(this, getString(R.string.toast_activity_detail_save_fail, cause), Toast.LENGTH_SHORT).show();
                    });
        });
    }

    @Override
    public void onBackPressed() {
        if (isDataSaved(data)) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_save_before_exit_title)
                .setMessage(R.string.dialog_save_before_exit_message)
                .setPositiveButton(R.string.dialog_save_before_exit_positive, (dialog, which) -> commitRecord(this::finish))
                .setNegativeButton(R.string.dialog_save_before_exit_negative, (dialog, which) -> super.onBackPressed())
                .setNeutralButton(R.string.dialog_common_cancel, null)
                .show();
    }

    @NonNull
    protected abstract Data createNewData();

    protected abstract void initializeView();

    protected abstract boolean asyncReadDetail(@NonNull Data data, byte[] key) throws Exception;

    protected abstract void asyncSaveDetail(@NonNull Data data, byte[] key) throws Exception;

    protected abstract void asyncRemoveDetail(@NonNull Data data, byte[] key) throws Exception;

    protected abstract void fillData(@NonNull Data data);

    protected abstract void onEditableChange(boolean editable);

    protected void beforeSaveHook(Runnable next) {
        next.run();
    }

    protected void fillPartData(@NonNull Data data) {
    }

    @NonNull
    protected String getCause(Throwable e, @SuppressWarnings("unused") From from) {
        String cause;
        if (e instanceof MainPassword.NoUserKeyException) {
            cause = getString(R.string.fail_no_main_password);
        } else {
            cause = getString(R.string.fail_unknown);
        }
        return cause;
    }

    protected boolean isDataSaved(@SuppressWarnings("unused") Data data) {
        return !lastSaveFail;
    }

    protected enum From {
        READ, SAVE, REMOVE
    }
}

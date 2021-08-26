package online.cszt0.pb.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.List;
import java.util.Objects;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import online.cszt0.androidcommonutils.view.CommonRecyclerViewAdapter;
import online.cszt0.androidcommonutils.view.ViewHolder;
import online.cszt0.pb.BuildConfig;
import online.cszt0.pb.R;
import online.cszt0.pb.bean.PasswordRecord;
import online.cszt0.pb.utils.Database;
import online.cszt0.pb.utils.MainPassword;

public class MainActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private final CommonRecyclerViewAdapter<PasswordRecord> commonRecyclerViewAdapter = new CommonRecyclerViewAdapter<PasswordRecord>(this, null, R.layout.item_simple_text) {
        @Override
        protected void bindView(ViewHolder viewHolder, PasswordRecord passwordRecord, int position, int viewType) {
            viewHolder.setTextViewText(R.id.text1, passwordRecord.getTitle());
            viewHolder.getView(R.id.text1).setOnClickListener(v -> onRecyclerViewItemClick(passwordRecord, position));
        }
    };

    private static final int REQUEST_DETAIL = 1;
    private static final int REQUEST_SETTING = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Database.isInitialize(this)) {
            startActivity(new Intent(this, HelloActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        swipeRefreshLayout = findViewById(R.id.swipe);
        recyclerView = findViewById(R.id.recycler_view);
        emptyView = findViewById(R.id.empty);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(commonRecyclerViewAdapter);
        this.findViewById(R.id.fab).setOnClickListener(this::onFabClick);

        swipeRefreshLayout.setOnRefreshListener(this::loadDatabase);
        loadDatabase();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!Database.isInitialize(this)) {
            startActivity(new Intent(this, HelloActivity.class));
            finish();
        }
    }

    private void onRecyclerViewItemClick(PasswordRecord passwordRecord, int position) {
        Intent intent = new Intent(this, RecordDetailActivity.class);
        intent.setAction(RecordDetailActivity.ACTION_MODIFY);
        intent.putExtra(RecordDetailActivity.EXTRA_KEY_RECORD, passwordRecord);
        startActivityForResult(intent, REQUEST_DETAIL);
    }

    private void onFabClick(View view) {
        Intent intent = new Intent(this, RecordDetailActivity.class);
        intent.setAction(RecordDetailActivity.ACTION_CREATE_NEW);
        startActivityForResult(intent, REQUEST_DETAIL);
    }

    @SuppressLint("CheckResult")
    private void loadDatabase() {
        swipeRefreshLayout.setRefreshing(true);
        MainPassword.getInstance().userKeyObservable(this)
                .observeOn(Schedulers.io())
                .map(k -> Database.getPasswordRecordList(this, k))
                .filter(Objects::nonNull)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::commitPasswordRecord, e -> {
                    if (BuildConfig.DEBUG) {
                        e.printStackTrace();
                    }
                    swipeRefreshLayout.setRefreshing(false);
                    if (commonRecyclerViewAdapter.getItemCount() == 0) {
                        emptyView.setVisibility(View.VISIBLE);
                    } else {
                        emptyView.setVisibility(View.GONE);
                    }
                    String cause;
                    if (e instanceof MainPassword.NoUserKeyException) {
                        cause = getString(R.string.fail_no_main_password);
                    } else {
                        cause = getString(R.string.fail_unknown);
                    }
                    Toast.makeText(this, getString(R.string.toast_activity_main_load_fail, cause), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.setting) {
            startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SETTING);
        }
        return super.onOptionsItemSelected(item);
    }

    private void commitPasswordRecord(List<PasswordRecord> passwordRecords) {
        swipeRefreshLayout.setRefreshing(false);
        commonRecyclerViewAdapter.resetDataSet(passwordRecords);
        if (passwordRecords.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
        recyclerView.post(() -> recyclerView.scrollTo(0, 0));
    }

    @Override
    protected void onDestroy() {
        MainPassword.getInstance().clearUserKey();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_DETAIL: {
                if (resultCode == RESULT_OK) {
                    loadDatabase();
                }
                break;
            }
            case REQUEST_SETTING: {
                if (resultCode == RESULT_OK) {
                    loadDatabase();
                }
            }
        }
    }
}
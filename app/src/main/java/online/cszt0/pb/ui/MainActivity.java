package online.cszt0.pb.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import online.cszt0.androidcommonutils.view.CommonRecyclerViewAdapter;
import online.cszt0.androidcommonutils.view.ViewHolder;
import online.cszt0.pb.BuildConfig;
import online.cszt0.pb.R;
import online.cszt0.pb.bean.Note;
import online.cszt0.pb.bean.PasswordRecord;
import online.cszt0.pb.utils.Database;
import online.cszt0.pb.utils.MainPassword;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private ViewPager2 viewPager;
    private FloatingActionButton floatingActionButton;
    private final Handler handler = new Handler();

    private static final int REQUEST_DETAIL = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Database.isInitialize(this)) {
            startActivity(new Intent(this, HelloActivity.class));
            finish();
            return;
        }
        if (Database.shouldUpgrade(this)) {
            startActivity(new Intent(this, UpgradeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        viewPager = findViewById(R.id.pager);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        floatingActionButton = findViewById(R.id.fab);

        viewPager.setAdapter(new FragmentStateAdapter(getSupportFragmentManager(), getLifecycle()) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return new KeyFragment();
                    case 1:
                        return new NoteFragment();
                    case 2:
                        return new ExploreFragment();
                    default:
                        return null;
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                int menuId = new int[]{
                        R.id.key, R.id.notes, R.id.explore
                }[position];
                bottomNavigationView.setSelectedItemId(menuId);
                handler.post(() -> getCurrentFragment().onUserChangeTo());
                if (position == 2) {
                    if (floatingActionButton.getVisibility() != View.GONE) {
                        floatingActionButton.startAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.scale_out));
                        floatingActionButton.setVisibility(View.GONE);
                    }
                } else {
                    if (floatingActionButton.getVisibility() != View.VISIBLE) {
                        floatingActionButton.startAnimation(AnimationUtils.loadAnimation(MainActivity.this, R.anim.scale_in));
                        floatingActionButton.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int position = -1;
            int itemId = item.getItemId();
            if (itemId == R.id.key) {
                position = 0;
            } else if (itemId == R.id.notes) {
                position = 1;
            } else if (itemId == R.id.explore) {
                position = 2;
            }
            if (viewPager.getCurrentItem() != position) {
                viewPager.setCurrentItem(position);
            }
            return true;
        });
        floatingActionButton.setOnClickListener(this::onFabClick);
    }

    private MainActivityFragment getCurrentFragment() {
        return (MainActivityFragment) getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!Database.isInitialize(this)) {
            startActivity(new Intent(this, HelloActivity.class));
            finish();
        }
    }

    private void onFabClick(View view) {
        int currentItem = viewPager.getCurrentItem();
        if (currentItem == 0) {
            Intent intent = new Intent(this, RecordDetailActivity.class);
            intent.setAction(RecordDetailActivity.ACTION_CREATE_NEW);
            startActivityForResult(intent, REQUEST_DETAIL);
        } else if (currentItem == 1) {
            Intent intent = new Intent(this, NoteDetailActivity.class);
            intent.setAction(NoteDetailActivity.ACTION_CREATE_NEW);
            startActivityForResult(intent, REQUEST_DETAIL);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == REQUEST_DETAIL) {
            handler.post(() -> getCurrentFragment().onDataChange());
        }
    }

    @Override
    protected void onDestroy() {
        MainPassword.getInstance().clearUserKey();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.setting) {
            startActivity(new Intent(this, SettingsActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }

    private abstract static class MainActivityFragment extends Fragment {
        /**
         * 当所启动的另一个 Activity 要求更新数据时，调用此方法
         */
        protected void onDataChange() {
        }

        protected void onUserChangeTo() {
        }
    }

    private static abstract class DataFragment<Data> extends MainActivityFragment {
        protected static final int REQUEST_DETAIL = 1;

        private SwipeRefreshLayout swipeRefreshLayout;
        private RecyclerView recyclerView;
        private CommonRecyclerViewAdapter<Data> recyclerViewAdapter;
        private View emptyView;

        private boolean firstLoad;

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View inflate = inflater.inflate(R.layout.fragment_main_datalist, container, false);
            swipeRefreshLayout = inflate.findViewById(R.id.swipe);
            recyclerView = inflate.findViewById(R.id.recycler_view);
            emptyView = inflate.findViewById(R.id.empty);

            swipeRefreshLayout.setOnRefreshListener(this::loadDatabase);
            recyclerViewAdapter = initRecyclerViewAdapter();
            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            recyclerView.setAdapter(recyclerViewAdapter);
            return inflate;
        }

        @NonNull
        protected abstract CommonRecyclerViewAdapter<Data> initRecyclerViewAdapter();

        @SuppressLint("CheckResult")
        private void loadDatabase() {
            swipeRefreshLayout.setRefreshing(true);
            firstLoad = true;
            //noinspection ResultOfMethodCallIgnored
            MainPassword.getInstance().userKeyObservable(getContext())
                    .observeOn(Schedulers.io())
                    .map(this::listData)
                    .filter(Objects::nonNull)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::commitData, e -> {
                        if (BuildConfig.DEBUG) {
                            e.printStackTrace();
                        }
                        swipeRefreshLayout.setRefreshing(false);
                        if (recyclerViewAdapter.getItemCount() == 0) {
                            emptyView.setVisibility(View.VISIBLE);
                        } else {
                            emptyView.setVisibility(View.GONE);
                        }
                        String cause = getCause(e);
                        Toast.makeText(getContext(), getString(R.string.toast_activity_main_load_fail, cause), Toast.LENGTH_SHORT).show();
                    });
        }

        private void commitData(List<Data> data) {
            swipeRefreshLayout.setRefreshing(false);
            recyclerViewAdapter.resetDataSet(data);
            if (data.isEmpty()) {
                emptyView.setVisibility(View.VISIBLE);
            } else {
                emptyView.setVisibility(View.GONE);
            }
            recyclerView.post(() -> recyclerView.scrollTo(0, 0));
        }

        protected abstract List<Data> listData(byte[] key);

        @Override
        protected void onUserChangeTo() {
            if (!firstLoad) {
                loadDatabase();
            }
        }

        @Override
        protected void onDataChange() {
            loadDatabase();
        }

        @NonNull
        protected String getCause(Throwable e) {
            if (e instanceof MainPassword.NoUserKeyException) {
                return getString(R.string.fail_no_main_password);
            } else {
                return getString(R.string.fail_unknown);
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode != RESULT_OK) {
                return;
            }
            if (requestCode == REQUEST_DETAIL) {
                loadDatabase();
            }
        }
    }

    public static class KeyFragment extends DataFragment<PasswordRecord> {
        @NonNull
        @Override
        protected CommonRecyclerViewAdapter<PasswordRecord> initRecyclerViewAdapter() {
            return new CommonRecyclerViewAdapter<PasswordRecord>(getContext(), null, R.layout.item_card_text) {
                @Override
                protected void bindView(ViewHolder viewHolder, PasswordRecord record, int position, int viewType) {
                    viewHolder.setTextViewText(R.id.text1, record.getTitle().replaceAll("[\r\n]", " "));
                    viewHolder.getContentView().setOnClickListener(v -> onItemClick(record));
                }
            };
        }

        @Override
        protected List<PasswordRecord> listData(byte[] key) {
            return Database.listRecord(getContext(), key);
        }

        private void onItemClick(PasswordRecord record) {
            Intent intent = new Intent(getContext(), RecordDetailActivity.class);
            intent.setAction(AbstractDetailActivity.ACTION_MODIFY);
            intent.putExtra(AbstractDetailActivity.EXTRA_KEY_DATA, record);
            startActivityForResult(intent, REQUEST_DETAIL);
        }
    }

    public static class NoteFragment extends DataFragment<Note> {
        @NonNull
        @Override
        protected CommonRecyclerViewAdapter<Note> initRecyclerViewAdapter() {
            return new CommonRecyclerViewAdapter<Note>(getContext(), null, R.layout.item_card_text) {
                @Override
                protected void bindView(ViewHolder viewHolder, Note note, int position, int viewType) {
                    viewHolder.setTextViewText(R.id.text1, note.getTitle().replaceAll("[\r\n]", " "));
                    viewHolder.getContentView().setOnClickListener(v -> onItemClick(note));
                }
            };
        }

        @Override
        protected List<Note> listData(byte[] key) {
            return Database.listNote(getContext(), key);
        }

        private void onItemClick(Note record) {
            Intent intent = new Intent(getContext(), NoteDetailActivity.class);
            intent.setAction(AbstractDetailActivity.ACTION_MODIFY);
            intent.putExtra(AbstractDetailActivity.EXTRA_KEY_DATA, record);
            startActivityForResult(intent, REQUEST_DETAIL);
        }
    }

    public static class ExploreFragment extends MainActivityFragment {

        private final List<ExplorerBean> explorerBeans;

        public ExploreFragment() {
            explorerBeans = Arrays.asList(
//                    new ExplorerBean(1, R.drawable.ic_export, "导出数据"),
//                    new ExplorerBean(2, R.drawable.ic_import, "导入数据"),
                    new ExplorerBean(3, R.drawable.ic_baseline_settings_24, R.string.explore_password_generator),
                    new ExplorerBean(-1, R.drawable.ic_baseline_more_horiz_24, R.string.explore_more)
            );
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View inflate = inflater.inflate(R.layout.fragment_main_explore, container, false);
            RecyclerView recyclerView = inflate.findViewById(R.id.recycler_view);
            recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
            recyclerView.setAdapter(new CommonRecyclerViewAdapter<ExplorerBean>(getContext(), explorerBeans, R.layout.item_explorer) {
                @Override
                protected void bindView(ViewHolder viewHolder, ExplorerBean explorerBean, int position, int viewType) {
                    ImageView imageView = viewHolder.getView(R.id.icon);
                    TextView textView = viewHolder.getView(R.id.title);

                    imageView.setImageResource(explorerBean.getDrawableId());
                    textView.setText(explorerBean.getName());
                    View contentView = viewHolder.getContentView();
                    contentView.setEnabled(explorerBean.getUniqueId() >= 0);
                    contentView.setOnClickListener(v -> onItemClick(explorerBean));
                }
            });
            return inflate;
        }

        private void onItemClick(ExplorerBean explorerBean) {
            if (explorerBean.getUniqueId() == 3) {
                startActivity(new Intent(getContext(), RandomPasswordActivity.class));
            }
        }

        private static class ExplorerBean {
            private final int uniqueId;
            @DrawableRes
            private final int drawableId;
            private final int name;

            private ExplorerBean(int uniqueId, int drawableId, int name) {
                this.uniqueId = uniqueId;
                this.drawableId = drawableId;
                this.name = name;
            }

            public int getUniqueId() {
                return uniqueId;
            }

            public int getDrawableId() {
                return drawableId;
            }

            public int getName() {
                return name;
            }
        }
    }
}
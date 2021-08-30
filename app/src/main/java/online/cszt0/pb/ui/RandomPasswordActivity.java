package online.cszt0.pb.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import online.cszt0.pb.R;
import online.cszt0.pb.bean.PasswordRecord;
import online.cszt0.pb.utils.PasswordUtils;

public class RandomPasswordActivity extends AppCompatActivity {

    private TextView passwordLengthView;
    private SeekBar passwordLengthSeekBar;
    private CheckBox upperLetter, lowerLetter, number, symbols;
    private TextView result;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_random_password);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        passwordLengthView = findViewById(R.id.length);
        passwordLengthSeekBar = findViewById(R.id.seekLength);
        upperLetter = findViewById(R.id.upper_letter);
        lowerLetter = findViewById(R.id.lower_letter);
        number = findViewById(R.id.number);
        symbols = findViewById(R.id.symbols);
        result = findViewById(R.id.result);
        Button save = findViewById(R.id.save);
        Button generate = findViewById(R.id.generate);
        Button copy = findViewById(R.id.copy);

        passwordLengthSeekBar.setMax(20);
        passwordLengthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                passwordLengthView.setText(getString(R.string.text_password_length, progress + 4));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        passwordLengthSeekBar.setProgress(8);

        upperLetter.setChecked(true);
        lowerLetter.setChecked(true);
        number.setChecked(true);
        symbols.setChecked(false);

        save.setOnClickListener(v -> savePassword());
        generate.setOnClickListener(v -> generatePassword());
        copy.setOnClickListener(v -> copyPassword());

        generatePassword();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void savePassword() {
        PasswordRecord record = new PasswordRecord();
        record.setPassword(result.getText().toString());
        Intent intent = new Intent(this, RecordDetailActivity.class);
        intent.setAction(AbstractDetailActivity.ACTION_CREATE_BY);
        intent.putExtra(AbstractDetailActivity.EXTRA_KEY_DATA, record);
        startActivity(intent);
    }

    private void copyPassword() {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Password", result.getText().toString()));
        Toast.makeText(this, R.string.toast_activity_random_password_copied, Toast.LENGTH_SHORT).show();
    }

    private void generatePassword() {
        int length = passwordLengthSeekBar.getProgress() + 4;
        int flag = 0;
        if (upperLetter.isChecked()) {
            flag |= PasswordUtils.RANDOM_UPPER_LETTER;
        }
        if (lowerLetter.isChecked()) {
            flag |= PasswordUtils.RANDOM_LOWER_LETTER;
        }
        if (number.isChecked()) {
            flag |= PasswordUtils.RANDOM_NUMBER;
        }
        if (symbols.isChecked()) {
            flag |= PasswordUtils.RANDOM_SYMBOL;
        }

        if (flag == 0) {
            Toast.makeText(this, R.string.toast_activity_random_password_type_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        String result = PasswordUtils.generateRandomPassword(length, flag);
        this.result.setText(result);
    }
}

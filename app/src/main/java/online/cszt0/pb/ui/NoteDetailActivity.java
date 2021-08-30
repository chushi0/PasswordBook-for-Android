package online.cszt0.pb.ui;

import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.Optional;

import online.cszt0.pb.R;
import online.cszt0.pb.bean.Note;
import online.cszt0.pb.utils.Database;

public class NoteDetailActivity extends AbstractDetailActivity<Note> {

    private EditText contentInput;

    @NonNull
    @Override
    protected Note createNewData() {
        return new Note();
    }

    @Override
    protected void initializeView() {
        setContentView(R.layout.activity_note_detail);
        contentInput = findViewById(R.id.input);
    }

    @Override
    protected boolean asyncReadDetail(@NonNull Note note, byte[] key) {
        return Database.readNote(this, note, key);
    }

    @Override
    protected void asyncSaveDetail(@NonNull Note note, byte[] key) {
        note.setContent(contentInput.getText().toString());
        Database.saveNote(this, note, key);
    }

    @Override
    protected void asyncRemoveDetail(@NonNull Note note, byte[] key) {
        Database.removeNote(this, note);
    }

    @Override
    protected void fillData(@NonNull Note note) {
        contentInput.setText(note.getContent());
    }

    @Override
    protected void onEditableChange(boolean editable) {
        contentInput.setFocusable(editable);
        contentInput.setFocusableInTouchMode(editable);
        if (editable) {
            contentInput.requestFocus();
        } else {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(contentInput.getWindowToken(), 0);
        }
    }

    @Override
    protected boolean isDataSaved(Note note) {
        return super.isDataSaved(note) &&
                ((note.getRowid() == 0 && contentInput.getText().toString().isEmpty()) ||
                        (note.getRowid() != 0 && Objects.equals(contentInput.getText().toString(), Optional.ofNullable(note.getContent()).orElse(""))));
    }
}

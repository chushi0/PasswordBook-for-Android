package online.cszt0.pb.utils;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import java.util.function.Supplier;

import online.cszt0.pb.R;

public class DialogProcess {
    private final Context context;
    private final Handler handler;
    private final DialogProcessLink head;

    private DialogProcess(Context context) {
        this.context = context;
        this.handler = new Handler();
        this.head = new NopLink();
    }

    public static DialogProcessLink create(Context context) {
        return new DialogProcess(context).head;
    }

    public abstract class DialogProcessLink {
        private DialogProcessLink next;

        protected DialogProcessLink() {
        }

        public DialogProcessLink abort(Supplier<Boolean> predicate, @StringRes int title, @StringRes int message) {
            return next = new AbortLink(predicate, title, message);
        }

        public DialogProcessLink question(Supplier<Boolean> predicate, @StringRes int title, @StringRes int message, @StringRes int positiveButton, @StringRes int negativeButton) {
            return next = new QuestionLink(predicate, title, message, positiveButton, negativeButton);
        }

        public DialogProcessLink custom(Supplier<Boolean> predicate, CustomLinkInterface linkInterface) {
            return next = new CustomLink(predicate, linkInterface);
        }

        public DialogProcessLink then(Runnable runnable) {
            return next = new ThenLink(runnable);
        }

        public void start() {
            head.run();
        }

        protected abstract void run();

        protected void runNext() {
            if (next != null) {
                handler.post(next::run);
            }
        }
    }

    private class CustomLink extends DialogProcessLink {

        private final Supplier<Boolean> predicate;
        @NonNull
        private final CustomLinkInterface customLinkInterface;

        private CustomLink(Supplier<Boolean> predicate, @NonNull CustomLinkInterface customLinkInterface) {
            this.predicate = predicate;
            this.customLinkInterface = customLinkInterface;
        }

        @Override
        protected void run() {
            if (predicate.get()) {
                customLinkInterface.run(this::runNext);
            } else {
                runNext();
            }
        }
    }

    private class NopLink extends DialogProcessLink {
        @Override
        protected void run() {
            runNext();
        }
    }

    private class AbortLink extends DialogProcessLink {

        private final Supplier<Boolean> predicate;
        private final @StringRes
        int title;
        private final @StringRes
        int message;

        public AbortLink(Supplier<Boolean> predicate, @StringRes int title, @StringRes int message) {
            this.predicate = predicate;
            this.title = title;
            this.message = message;
        }

        @Override
        protected void run() {
            if (!predicate.get()) {
                runNext();
                return;
            }
            new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(R.string.dialog_common_ok, null)
                    .show();
        }
    }

    private class QuestionLink extends DialogProcessLink {
        private final Supplier<Boolean> predicate;
        private final @StringRes
        int title;
        private final @StringRes
        int message;
        private final @StringRes
        int positiveButton;
        private final @StringRes
        int negativeButton;

        private QuestionLink(Supplier<Boolean> predicate, @StringRes int title, @StringRes int message, @StringRes int positiveButton, @StringRes int negativeButton) {
            this.predicate = predicate;
            this.title = title;
            this.message = message;
            this.positiveButton = positiveButton;
            this.negativeButton = negativeButton;
        }

        @Override
        protected void run() {
            if (!predicate.get()) {
                runNext();
                return;
            }
            new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(positiveButton, (dialog, which) -> runNext())
                    .setNegativeButton(negativeButton, null)
                    .show();
        }
    }

    private class ThenLink extends DialogProcessLink {
        private final Runnable runnable;

        private ThenLink(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        protected void run() {
            runnable.run();
            runNext();
        }
    }

    public interface CustomLinkInterface {
        void run(Continue contine);
    }

    public interface Continue {
        void runNext();
    }
}

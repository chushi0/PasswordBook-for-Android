package online.cszt0.pb.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Pair;

import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.crypto.NoSuchPaddingException;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import online.cszt0.pb.BuildConfig;
import online.cszt0.pb.R;

public final class MainPassword {
    private static final MainPassword instance = new MainPassword();
    private final Handler handler = new Handler(Looper.getMainLooper(), this::handleMessage);

    private static final int WHAT_CLEAR_KEY = 1;
    private static final int WHAT_USE_KEY_INPUT_PASSWORD = 2;

    private final byte[] randomKey = new byte[32];
    private final byte[] userKey = new byte[32];
    private boolean userKeyValid;

    private static final long clearKeyAfter = 5 * 60 * 1000; // 5分钟未使用清除内存中的密钥

    private Context applicationContext;

    private MainPassword() {
        clearUserKey();
    }

    public static MainPassword getInstance() {
        return instance;
    }

    public void setApplicationContext(Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    private boolean handleMessage(Message message) {
        switch (message.what) {
            case WHAT_CLEAR_KEY: {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
                if (preferences.getBoolean("secure_auto_clear", true)) {
                    clearUserKey();
                }
                break;
            }
            case WHAT_USE_KEY_INPUT_PASSWORD: {
                UseKeyParam param = (UseKeyParam) message.obj;
                requirePasswordInput(param.context, param.password, param.consumer, param.cancel);
                break;
            }
        }
        return true;
    }

    /**
     * 记录用户密钥
     *
     * @param key 密钥
     */
    public void setUserKey(byte[] key) {
        if (BuildConfig.DEBUG) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                throw new IllegalStateException();
            }
        }
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(randomKey);
        for (int i = 0; i < 32; i++) {
            userKey[i] = (byte) (key[i] ^ randomKey[i]);
        }
        userKeyValid = true;
        handler.removeMessages(WHAT_CLEAR_KEY);
        postClearUserKey();
    }

    /**
     * 获取用户密钥
     *
     * @param key 密钥输出
     * @return 如果成功获取，返回 true
     */
    private boolean getUserKey(byte[] key) {
        if (BuildConfig.DEBUG) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                throw new IllegalStateException();
            }
        }
        if (!userKeyValid) {
            return false;
        }
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(key);
        for (int i = 0; i < 32; i++) {
            byte b = (byte) (userKey[i] ^ randomKey[i]);
            randomKey[i] = key[i];
            key[i] = b;
            userKey[i] = (byte) (randomKey[i] ^ key[i]);
        }
        handler.removeMessages(WHAT_CLEAR_KEY);
        postClearUserKey();
        return true;
    }

    /**
     * 获取并使用密钥，若当前密钥未保存则要求用户重新输入
     *
     * @param context  上下文（用于展示对话框）
     * @param consumer 获取密钥回调
     * @param cancel   用户放弃输入回调
     */
    private void useUserKey(Context context, Consumer<byte[]> consumer, Runnable cancel) {
        byte[] key = new byte[32];
        boolean hasKey = getUserKey(key);
        if (hasKey) {
            consumer.accept(key);
            return;
        }

        UseKeyParam useKeyParam = new UseKeyParam(context, consumer, cancel, null);
        Message message = handler.obtainMessage(WHAT_USE_KEY_INPUT_PASSWORD);
        message.obj = useKeyParam;
        message.sendToTarget();
    }

    /**
     * 创建 RxJava 的发布者
     * <p>
     * 在 {@link Observable#subscribe()} 类方法被调用时，发布者会将缓存的密码本主密码发布。
     * 若当前没有缓存主密码，则会要求用户输入主密码，验证通过后会将主密码缓存并发布。
     * <p>
     * 若当前没有缓存主密码，并且用户也没有输入时，则会在 RxJava 架构中抛出 {@link NoUserKeyException}
     *
     * @param context 要求用户输入时显示对话框所用的上下文
     * @return RxJava 的发布者
     * @see Observable
     * @see NoUserKeyException
     */
    public Observable<byte[]> userKeyObservable(Context context) {
        //noinspection ResultOfMethodCallIgnored
        return Observable.create(emitter -> Observable.just(0)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(e -> useUserKey(context, key -> {
                    emitter.onNext(key);
                    emitter.onComplete();
                }, () -> emitter.onError(new NoUserKeyException()))));
    }

    /**
     * 强制用户输入并确认主密码，并以 RxJava 的发布者方式返回
     *
     * @param context 要求用户输入时显示对话框所用的上下文
     * @return RxJava 的发布者
     */
    public Observable<byte[]> forceInputPassword(Context context) {
        return Observable.create(emitter -> {
            Consumer<byte[]> consumer = key -> {
                emitter.onNext(key);
                emitter.onComplete();
            };
            Runnable cancel = () -> emitter.onError(new NoUserKeyException());

            UseKeyParam useKeyParam = new UseKeyParam(context, consumer, cancel, null);
            Message message = handler.obtainMessage(WHAT_USE_KEY_INPUT_PASSWORD);
            message.obj = useKeyParam;
            message.sendToTarget();
        });
    }

    public <T> ObservableSource<T> userKeyObservableSource(Context context, Function<byte[], T> emit) {
        //noinspection ResultOfMethodCallIgnored
        return Observable.create(emitter -> userKeyObservable(context)
                .subscribe(key -> emitter.onNext(emit.apply(key)), emitter::onError, emitter::onComplete));
    }

    @SuppressLint("CheckResult")
    private void requirePasswordInput(Context context, String pwd, Consumer<byte[]> consumer, Runnable cancel) {
        //noinspection ResultOfMethodCallIgnored
        PasswordDialog dialog = PasswordDialog.create(context, inputPwd -> Observable.just(inputPwd)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map(p -> p.length() > 50 ? "" : p)
                .map(Crypto::userInput2AesKey)
                .map(password -> new Pair<>(password, Database.checkPassword(context, password)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(pair -> {
                    if (!pair.second) {
                        Message message = handler.obtainMessage(WHAT_USE_KEY_INPUT_PASSWORD);
                        message.obj = new UseKeyParam(context, consumer, cancel, inputPwd);
                        message.sendToTarget();
                    } else {
                        byte[] key = pair.first;
                        setUserKey(key);
                        consumer.accept(key);
                    }
                }), dialog1 -> cancel.run());
        dialog.setTitle(R.string.dialog_password_main_input);
        dialog.setCheckSafePassword(false);
        try {
            if (Database.checkBiometricOpen(context)) {
                dialog.setSupportBiometric(context.getString(R.string.text_biometric_password), null, Database.getCryptoObject(context), pwd != null, cryptoObject -> {
                    //noinspection ResultOfMethodCallIgnored
                    Observable.just(cryptoObject)
                            .observeOn(Schedulers.io())
                            .map(cryptoObject1 -> Database.decodeBiometricPassword(context, cryptoObject))
                            .map(password -> new Pair<>(password, Database.checkPassword(context, password)))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(pair -> {
                                if (!pair.second) {
                                    Message message = handler.obtainMessage(WHAT_USE_KEY_INPUT_PASSWORD);
                                    message.obj = new UseKeyParam(context, consumer, cancel, pwd);
                                    message.sendToTarget();
                                } else {
                                    byte[] key = pair.first;
                                    setUserKey(key);
                                    consumer.accept(key);
                                }
                            });
                });
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | UnrecoverableKeyException | CertificateException | KeyStoreException | IOException | InvalidKeyException | InvalidAlgorithmParameterException e) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
        }
        if (pwd != null) {
            dialog.setInitPassword(pwd);
            dialog.setPasswordVisible(true);
            dialog.setErrorMessage(context.getString(R.string.text_wrong_password));
        }
        dialog.show();
    }

    /**
     * 清空用户密钥
     */
    public void clearUserKey() {
        if (BuildConfig.DEBUG) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                throw new IllegalStateException();
            }
        }
        userKeyValid = false;
        Random random = new Random();
        random.nextBytes(randomKey);
        random.nextBytes(userKey);
        handler.removeMessages(WHAT_CLEAR_KEY);
    }

    /**
     * 5分钟延迟后，清除缓存的密码
     */
    public void postClearUserKey() {
        handler.sendEmptyMessageDelayed(WHAT_CLEAR_KEY, clearKeyAfter);
    }

    /**
     * 当获取主密码时，若当前缓存没有密码，并且用户拒绝输入，则会抛出该异常。
     *
     * @see #userKeyObservable(Context)
     */
    public static class NoUserKeyException extends Exception {
    }

    private static class UseKeyParam {
        private final Context context;
        private final Consumer<byte[]> consumer;
        private final Runnable cancel;
        private final String password;

        private UseKeyParam(Context context, Consumer<byte[]> consumer, Runnable cancel, String password) {
            this.context = context;
            this.consumer = consumer;
            this.cancel = cancel;
            this.password = password;
        }
    }
}

package online.cszt0.pb.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.function.Consumer;
import java.util.function.Function;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import online.cszt0.pb.R;

public class BiometricUtils {
    public static boolean isBiometricHardwareSupported(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        int canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public static void startBiometricAuthenticate(Context context, String title, String subtitle, BiometricPrompt.CryptoObject cryptoObject, Consumer<BiometricPrompt.CryptoObject> onSuccess, Consumer<String> onFail) {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(context.getText(R.string.dialog_common_cancel))
                .build();
        BiometricPrompt biometricPrompt = new BiometricPrompt((FragmentActivity) context, ContextCompat.getMainExecutor(context), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                onSuccess.accept(result.getCryptoObject());
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                onFail.accept(errString.toString());
            }
        });
        biometricPrompt.authenticate(promptInfo, cryptoObject);
    }

    public static <T> ObservableSource<T> authenticateObservableSource(Context context, String title, String subtitle, BiometricPrompt.CryptoObject cryptoObject, Function<BiometricPrompt.CryptoObject, T> emit) {
        //noinspection ResultOfMethodCallIgnored
        return Observable.create(emitter -> Observable.just(0).observeOn(AndroidSchedulers.mainThread())
                .subscribe(key -> startBiometricAuthenticate(context, title, subtitle, cryptoObject, cryptoObject1 -> {
                    emitter.onNext(emit.apply(cryptoObject1));
                    emitter.onComplete();
                }, e -> emitter.onError(new BiometricFailException(e))), emitter::onError));
    }

    public static class BiometricFailException extends RuntimeException {
        private final String errString;

        public BiometricFailException(String errString) {
            super(errString);
            this.errString = errString;
        }

        public String getErrString() {
            return errString;
        }
    }
}

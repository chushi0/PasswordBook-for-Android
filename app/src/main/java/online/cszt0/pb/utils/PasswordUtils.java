package online.cszt0.pb.utils;

import android.util.Pair;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

import online.cszt0.pb.BuildConfig;

public class PasswordUtils {
    /**
     * 随机字符集中包含数字
     *
     * @see #generateRandomPassword(int, int)
     */
    public static final int RANDOM_NUMBER = 1;
    /**
     * 随机字符集中包含小写字母
     *
     * @see #generateRandomPassword(int, int)
     */
    public static final int RANDOM_LOWER_LETTER = 2;
    /**
     * 随机字符集中包含大写字母
     *
     * @see #generateRandomPassword(int, int)
     */
    public static final int RANDOM_UPPER_LETTER = 4;
    /**
     * 随机字符集中包含特殊符号
     *
     * @see #generateRandomPassword(int, int)
     */
    public static final int RANDOM_SYMBOL = 8;

    /**
     * 安全的密码
     */
    public static final int WEAK_NONE = 0;
    /**
     * 不安全的密码：类型较少
     */
    public static final int WEAK_FEW_TYPE = 1;
    /**
     * 不安全的密码：太短
     */
    public static final int WEAK_TOO_SHORT = 2;
    /**
     * 不安全的密码：包含非标准字符
     */
    public static final int WEAK_NOT_ASCII = 4;

    /**
     * 推荐密码最短长度
     */
    public static final int SUGGEST_PASSWORD_LENGTH = 12;
    /**
     * 推荐类型数量
     */
    public static final int SUGGEST_TYPE_COUNT = 3;

    private static final int RANDOM_MARK = RANDOM_NUMBER | RANDOM_LOWER_LETTER | RANDOM_UPPER_LETTER | RANDOM_SYMBOL;
    private static final char[] SYMBOLS = "!\"#$%&'()*+,-./;<=>?@[\\]^_`{|}~".toCharArray();

    static {
        Arrays.sort(SYMBOLS);
    }

    /**
     * 生成随机密码
     *
     * @param length     密码长度
     * @param randomFlag 密码字符集
     * @return 随机密码
     * @see #RANDOM_NUMBER
     * @see #RANDOM_LOWER_LETTER
     * @see #RANDOM_UPPER_LETTER
     * @see #RANDOM_SYMBOL
     */
    public static String generateRandomPassword(int length, int randomFlag) {
        if (BuildConfig.DEBUG) {
            randomFlag &= RANDOM_MARK;
            if (randomFlag == 0) {
                throw new IllegalArgumentException();
            }
        }

        int charset = 0;
        if ((randomFlag & RANDOM_NUMBER) != 0) charset += 10;
        if ((randomFlag & RANDOM_LOWER_LETTER) != 0) charset += 26;
        if ((randomFlag & RANDOM_UPPER_LETTER) != 0) charset += 26;
        if ((randomFlag & RANDOM_SYMBOL) != 0) charset += SYMBOLS.length;

        StringBuilder builder = new StringBuilder();
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < length; i++) {
            int v = random.nextInt(charset);
            if ((randomFlag & RANDOM_NUMBER) != 0) {
                if (v < 10) {
                    builder.append((char) ('0' + v));
                    continue;
                }
                v -= 10;
            }
            if ((randomFlag & RANDOM_LOWER_LETTER) != 0) {
                if (v < 26) {
                    builder.append((char) ('a' + v));
                    continue;
                }
                v -= 26;
            }
            if ((randomFlag & RANDOM_UPPER_LETTER) != 0) {
                if (v < 26) {
                    builder.append((char) ('A' + v));
                    continue;
                }
                v -= 26;
            }
            builder.append(SYMBOLS[v]);
        }
        return builder.toString();
    }

    /**
     * 检查密码是否安全
     *
     * @param password 密码
     * @return 不安全因素
     * @see #WEAK_NONE
     * @see #WEAK_FEW_TYPE
     * @see #WEAK_TOO_SHORT
     * @see #WEAK_NOT_ASCII
     */
    public static int isWeakPassword(String password) {
        int result = WEAK_NONE;
        int type = 0;

        int length = password.length();
        if (length < SUGGEST_PASSWORD_LENGTH) {
            result |= WEAK_TOO_SHORT;
        }

        for (int i = 0; i < length; i++) {
            char c = password.charAt(i);
            if (c >= '0' && c <= '9') {
                type |= RANDOM_NUMBER;
            } else if (c >= 'a' && c <= 'z') {
                type |= RANDOM_LOWER_LETTER;
            } else if (c >= 'A' && c <= 'Z') {
                type |= RANDOM_UPPER_LETTER;
            } else if (Arrays.binarySearch(SYMBOLS, c) >= 0) {
                type |= RANDOM_SYMBOL;
            } else {
                result |= WEAK_NOT_ASCII;
            }
        }

        if ((result & WEAK_NOT_ASCII) == 0 && Integer.bitCount(type) < SUGGEST_TYPE_COUNT) {
            result |= WEAK_FEW_TYPE;
        }

        return result;
    }

    /**
     * 校验密码
     *
     * @param key    密码
     * @param uuid   uuid
     * @param verify verify
     * @return 是否一致
     */
    public static boolean checkPassword(byte[] key, String uuid, String verify) {
        if (uuid == null || verify == null) {
            return false;
        }
        try {
            verify = Crypto.decryptData(verify, key);
        } catch (IndexOutOfBoundsException e) {
            // 密钥错误时会发生越界异常
            return false;
        }
        if (uuid.length() != verify.length()) {
            return false;
        }
        int len = uuid.length();

        // 无论哪一位不一致，保证总比较时间相等
        int v = 0;
        for (int i = 0; i < len; i++) {
            v |= uuid.charAt(i) ^ verify.charAt(i);
        }
        return v == 0;
    }

    /**
     * 编码密码
     *
     * @param key 密码
     * @return first: uuid, second: verify
     */
    public static Pair<String, String> encodePassword(byte[] key) {
        String uuid = UUID.randomUUID().toString();
        String verify = Crypto.encryptData(uuid, key);
        return new Pair<>(uuid, verify);
    }
}

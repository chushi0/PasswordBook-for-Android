package online.cszt0.pb.utils;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

public class Crypto {
    static {
        System.loadLibrary("crypto");
    }

    /**
     * 使用密钥加密数据
     *
     * @param message 明文
     * @param key     密钥
     * @return 密文
     */
    public static byte[] encryptStringData(String message, byte[] key) {
        return encryptData(message.getBytes(StandardCharsets.UTF_8), key);
    }

    public static byte[] encryptData(byte[] message, byte[] key) {
        // 前面添加随机数据，保证相同内容多次加密的加密结果不一样
        byte[] iv = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        byte[] plain = new byte[message.length + 16];
        System.arraycopy(iv, 0, plain, 0, 16);
        System.arraycopy(message, 0, plain, 16, message.length);
        int len = plain.length;
        if (len % 16 == 0) {
            len += 16;
        } else {
            len += 16 - len % 16;
        }

        byte[] data = new byte[len];
        encrypt(key, plain, data);
        return data;
    }

    /**
     * 使用密钥解密数据
     *
     * @param data 密文
     * @param key  密钥
     * @return 明文
     */
    public static String decryptStringData(byte[] data, byte[] key) {
        byte[] plain = new byte[data.length];
        int len = decrypt(key, plain, data);
        return new String(plain, 16, len - 16, StandardCharsets.UTF_8);
    }

    public static byte[] decryptData(byte[] data, byte[] key) {
        byte[] plain = new byte[data.length];
        int len = decrypt(key, plain, data);
        return Arrays.copyOfRange(plain, 16, len);
    }

    /**
     * 将用户输入转化为 256 位密钥
     *
     * @param input 用户输入
     * @return 256 位密钥
     */
    public static byte[] userInput2AesKey(String input) {
        byte[] inputArray = input.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[32];
        key(inputArray, key);
        return key;
    }

    /**
     * 将输入转化为 256 位密钥。函数实际上将输入进行了 SHA-256 散列
     *
     * @param userInput 输入
     * @param key       用于输出 256 位密钥，务必保证长度为 32
     */
    private static native void key(@NonNull byte[] userInput, @NonNull byte[] key);

    /**
     * 使用 AES-256 算法加密数据
     *
     * @param key   256 位密钥（长度32位）
     * @param plain 明文
     * @param data  密文输出（需大于明文长度，且为16的倍数）
     * @return 密文所需空间。若密文所需空间不足，则不进行加密操作
     */
    private static native int encrypt(@NonNull byte[] key, @NonNull byte[] plain, @NonNull byte[] data);

    /**
     * 使用 AES-256 算法解密数据
     *
     * @param key   256 位密钥（长度32位）
     * @param plain 明文输出（需至少与明文长度相同）
     * @param data  密文
     * @return 明文长度。若明文所需空间不足，则不进行解密操作
     */
    private static native int decrypt(@NonNull byte[] key, @NonNull byte[] plain, @NonNull byte[] data);

    /**
     * 使用 Base64 编码
     *
     * @param data 数据
     * @return Base64 编码结果
     */
    public static String base64Encrypt(byte[] data) {
        final char[] table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        int len = data.length;
        int remain;
        if (len % 3 == 0) {
            remain = 0;
        } else {
            remain = 3 - len % 3;
        }
        int outLen = (len + remain) / 3 * 4 + remain;
        char[] out = new char[outLen];
        int offset = 0;
        for (int i = 0; i < len; i += 3) {
            int val = (data[i] & 0xff) << 16;
            if (i + 1 < len) {
                val |= (data[i + 1] & 0xff) << 8;
            }
            if (i + 2 < len) {
                val |= data[i + 2] & 0xff;
            }

            out[offset++] = table[(val >> 18) & 0x3F];
            out[offset++] = table[(val >> 12) & 0x3F];
            if (i + 1 < len) {
                out[offset++] = table[(val >> 6) & 0x3F];
            } else {
                out[offset++] = '=';
            }
            if (i + 2 < len) {
                out[offset++] = table[val & 0x3F];
            } else {
                out[offset++] = '=';
            }
        }
        return new String(out, 0, offset);
    }

    /**
     * 使用 Base64 解码
     *
     * @param data 数据
     * @return 原始数据
     */
    public static byte[] base64Decrypt(String data) {
        final byte[] table = {
                62, // '+'
                0, 0, 0,
                63, // '/'
                52, 53, 54, 55, 56, 57, 58, 59, 60, 61, // '0'-'9'
                0, 0, 0, 0, 0, 0, 0,
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
                13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, // 'A'-'Z'
                0, 0, 0, 0, 0, 0,
                26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38,
                39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, // 'a'-'z'
        };

        char[] chars = data.toCharArray();
        int len = chars.length;
        int outLen = len / 4 * 3;
        byte[] out = new byte[outLen];

        int offset = 0;
        int c = 0, v = 0;
        for (char e : chars) {
            if (e != '=') {
                c++;
                v |= (table[e - '+'] & 0x3f) << (6 * (4 - c));
            }

            if (e == '=' || c == 4) {
                out[offset++] = (byte) ((v >> 16) & 0xff);
                if (c > 2) {
                    out[offset++] = (byte) ((v >> 8) & 0xff);
                }
                if (c > 3) {
                    out[offset++] = (byte) (v & 0xff);
                }
                if (e == '=') {
                    break;
                }
                c = 0;
                v = 0;
            }
        }
        return Arrays.copyOf(out, offset);
    }
}

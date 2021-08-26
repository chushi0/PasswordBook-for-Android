package online.cszt0.pb.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import online.cszt0.pb.BuildConfig;

public class IOUtils {
    public static String readFully(File file) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
                builder.append("\n");
            }
            return builder.toString();
        }
    }

    public static boolean checkChecksum(File file, String checksum) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = fileInputStream.read(buf)) > 0) {
                    digest.update(buf, 0, len);
                }
            }
            String result = Crypto.base64Encrypt(digest.digest());
            return !result.equals(checksum);
        } catch (NoSuchAlgorithmException | IOException e) {
            if (BuildConfig.DEBUG) {
                e.printStackTrace();
            }
            return false;
        }
    }

    public static void deleteFile(File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                deleteFile(f);
            }
        }
        file.delete();
    }
}

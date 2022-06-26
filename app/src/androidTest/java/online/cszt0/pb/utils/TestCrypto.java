package online.cszt0.pb.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TestCrypto {
    @Test
    public void testKey() {
        assertEquals("a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3", asString(Crypto.userInput2AesKey("123")));
    }

    @Test
    public void testEncryptAndDecrypt() {
        byte[] key = Crypto.userInput2AesKey("key");
        String plainMessage = "hello";
        byte[] encrypt = Crypto.encryptStringData(plainMessage, key);
        String decrypt = Crypto.decryptStringData(encrypt, key);
        assertEquals(plainMessage, decrypt);
    }

    @Test
    public void testLargeEncryptAndDecrypt() {
        byte[] key = Crypto.userInput2AesKey("key");
        String plainMessage = "Clang-Tidy: Narrowing conversion from constant value 2773480762 (0xA54FF53A) of type 'unsigned int' to signed type 'long' is implementation-defined";
        byte[] encrypt = Crypto.encryptStringData(plainMessage, key);
        String decrypt = Crypto.decryptStringData(encrypt, key);
        assertEquals(plainMessage, decrypt);
    }

    @Test
    public void testBase64Encrypt_4() {
        assertEquals("MTIzNA==", Crypto.base64Encrypt("1234".getBytes()));
    }

    @Test
    public void testBase64Encrypt_5() {
        assertEquals("MTIzNDU=", Crypto.base64Encrypt("12345".getBytes()));
    }

    @Test
    public void testBase64Encrypt_6() {
        assertEquals("MTIzNDU2", Crypto.base64Encrypt("123456".getBytes()));
    }

    @Test
    public void testBase64Decrypt_4() {
        assertArrayEquals("1234".getBytes(), Crypto.base64Decrypt("MTIzNA=="));
    }

    @Test
    public void testBase64Decrypt_5() {
        assertArrayEquals("12345".getBytes(), Crypto.base64Decrypt("MTIzNDU="));
    }

    @Test
    public void testBase64Decrypt_6() {
        assertArrayEquals("123456".getBytes(), Crypto.base64Decrypt("MTIzNDU2"));
    }

    private String asString(byte[] key) {
        char[] v = {
                '0', '1', '2', '3', '4', '5', '6', '7',
                '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        };
        StringBuilder builder = new StringBuilder();
        for (byte k : key) {
            int n = k & 0xff;
            builder.append(v[n / 16]).append(v[n % 16]);
        }
        return builder.toString();
    }
}

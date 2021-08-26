package online.cszt0.pb.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class TestPasswordUtils {
    @Test
    public void generatePassword() {
        String pwd = PasswordUtils.generateRandomPassword(8, PasswordUtils.RANDOM_NUMBER | PasswordUtils.RANDOM_UPPER_LETTER | PasswordUtils.RANDOM_LOWER_LETTER | PasswordUtils.RANDOM_SYMBOL);
        assertEquals(8, pwd.length());
    }
}

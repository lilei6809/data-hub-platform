package io.datahub.platform.iamprovisioning.util;

import java.security.SecureRandom;
import java.util.Base64;

public class SecureRandomPasswordGenerator {
    private static final int LENGTH = 20; // Base64编码后的长度

    public static void main(String[] args) {
        System.out.println(generateSecureRandomPassword());
    }

    public static String generateSecureRandomPassword() {
        SecureRandom random = new SecureRandom();
        byte[] passwordBytes = new byte[LENGTH];
        random.nextBytes(passwordBytes);

        // Base64编码，然后去掉末尾的'='字符，因为它可能会引起一些问题
        String password = Base64.getEncoder().encodeToString(passwordBytes).replaceAll("=", "");

        // Base64编码后的字符串可能会比原始字节数组长，所以我们需要截取所需长度的部分
        return password.substring(0, Math.min(password.length(), LENGTH));
    }
}


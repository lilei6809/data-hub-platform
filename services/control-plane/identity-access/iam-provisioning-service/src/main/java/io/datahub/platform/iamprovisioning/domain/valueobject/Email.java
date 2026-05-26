package io.datahub.platform.iamprovisioning.domain.valueobject;

import io.datahub.platform.iamprovisioning.domain.exception.DomainValidationException;

import java.util.regex.Pattern;

public record Email(String value) {

    public static final int MAX_LENGTH = 254; // RFC 5321

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("Email", "must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException("Email", "must not exceed %d characters".formatted(MAX_LENGTH));
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new DomainValidationException("Email", "invalid format");
        }
    }

    public static Email of(String value) {
        return new Email(value);
    }

    /** 脱敏展示，用于日志和错误消息，避免原始邮箱泄漏 */
    public String masked() {
        int atIndex = value.indexOf('@');
        String local = value.substring(0, atIndex);
        String domain = value.substring(atIndex);
        String maskedLocal = switch (local.length()) {
            case 1 -> "***";
            case 2 -> local.charAt(0) + "***";
            default -> local.charAt(0) + "***" + local.charAt(local.length() - 1);
        };
        return maskedLocal + domain;
    }

    @Override
    public String toString() {
        // 关键：toString 调用 masked，不暴露完整邮箱
        // 这样 log.info("{}", email) 也是安全的
        return "Email[" + masked() + "]";
    }
}

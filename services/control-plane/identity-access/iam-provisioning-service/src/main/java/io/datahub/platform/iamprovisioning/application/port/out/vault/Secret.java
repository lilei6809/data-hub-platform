package io.datahub.platform.iamprovisioning.application.port.out.vault;

// Secret 值对象的关键设计：它永远不会泄漏明文
public final class Secret {
    private final String value; // 内部持有明文

    public Secret(String value) {
        this.value = value;
    }

    // toString 是脱敏的！无论是日志还是异常消息都不会泄漏
    @Override
    public String toString() {
        return "Secret[***REDACTED***]";
    }

    // 只有显式调用 .reveal() 才能拿到明文
    public String reveal() {
        return value;
    }
}

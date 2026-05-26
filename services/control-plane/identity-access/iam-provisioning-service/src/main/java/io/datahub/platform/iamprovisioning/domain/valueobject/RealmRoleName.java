package io.datahub.platform.iamprovisioning.domain.valueobject;

public record RealmRoleName(String roleName) {

    public static   RealmRoleName of(String roleName) {
        return new RealmRoleName(roleName);
    }
}

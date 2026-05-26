package io.datahub.platform.iamprovisioning.domain.valueobject;

public record UserId(String userId) {

    public static UserId of(String userId){
        return new UserId(userId);
    }
}

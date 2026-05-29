package io.datahub.platform.iamprovisioning.domain.valueobject;

public record UserId(String value) {

    public static UserId of(String value){
        return new UserId(value);
    }
}

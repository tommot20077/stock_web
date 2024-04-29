package xyz.dowob.stockweb.Enum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author yuan
 */
@Getter
@AllArgsConstructor
public enum Permission {
    USER_READ("user:read"),
    USER_CREATE("user:create"),
    USER_UPDATE("user:update"),
    USER_DELETE("user:delete");
    private final String permission;

}

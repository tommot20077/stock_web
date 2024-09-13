package xyz.dowob.stockweb.Enum;

import lombok.Getter;

/**
 * @author yuan
 * 操作類型
 */
@Getter
public enum OperationType {
    ADD("新增"),
    REMOVE("刪除"),
    UPDATE("更新"),
    OTHER("其他");

    private final String description;

    OperationType(String description) {
        this.description = description;
    }

}

package xyz.dowob.stockweb.Model.User;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.Data;
import xyz.dowob.stockweb.Enum.Priority;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * @author yuan
 * 待辦事項
 * 實現Serializable, 用於序列化
 * 1. id : 待辦事項編號
 * 2. content : 內容
 * 3. priority : 優先級
 * 4. dueDate : 到期時間
 * 5. reminder : 是否提醒
 * 6. reminderTime : 提醒時間
 * 7. user : 使用者
 */
@Entity
@Data
@Table(name = "todo_list")
public class Todo implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,
            columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Column(nullable = false)
    private OffsetDateTime dueDate;

    @Column(nullable = false)
    private boolean reminder = false;

    private OffsetDateTime reminderTime;

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
                      property = "id")
    @JoinColumn(name = "user_id",
                nullable = false)
    @ManyToOne(fetch = FetchType.EAGER)
    private User user;
}

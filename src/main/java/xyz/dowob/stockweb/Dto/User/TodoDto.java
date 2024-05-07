package xyz.dowob.stockweb.Dto.User;

import lombok.Data;
import xyz.dowob.stockweb.Enum.Priority;
import xyz.dowob.stockweb.Enum.Role;
import xyz.dowob.stockweb.Model.User.Todo;
import xyz.dowob.stockweb.Model.User.User;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @author yuan
 * 用於傳遞待辦事項的資料
 * 1. id: 待辦事項ID
 * 2. content: 內容
 * 3. priority: 優先級
 * 4. dueDate: 到期日
 * 5. isDone: 是否完成
 * 6. isReminder: 是否提醒
 * 7. reminderTime: 提醒時間
 * 8. userId: 用戶ID
 */
@Data
public class TodoDto {
    private Long id;

    private String content;

    private String priority;

    private String dueDate;

    private boolean isDone;

    private String isReminder;

    private String reminderTime;

    private Long userId;

    /**
     * 將待辦事項轉換為TodoDto
     *
     * @param todoDto 待辦事項資料
     * @param user    用戶
     *
     * @return 待辦事項
     */
    public Todo toEntity(TodoDto todoDto, User user) {
        Todo todo = new Todo();
        todo.setContent(todoDto.getContent());
        todo.setPriority(Priority.valueOf(todoDto.getPriority()));
        todo.setUser(user);

        if (("true").equals(isReminder)) {
            if (user.getRole() == Role.UNVERIFIED_USER) {
                throw new RuntimeException("未驗證用戶不可使用提醒功能");
            }

            OffsetDateTime userUtcDueDate = parseToUtc(todoDto.getDueDate(), user);
            OffsetDateTime userUtcReminderTime = parseToUtc(todoDto.getReminderTime(), user);
            todo.setDueDate(userUtcDueDate);
            todo.setReminderTime(userUtcReminderTime);
            todo.setReminder(true);
        } else {
            OffsetDateTime userUtcDueDate = parseToUtc(todoDto.getDueDate(), user);
            todo.setDueDate(userUtcDueDate);
            todo.setReminder(false);
        }
        return todo;
    }

    /**
     * 將時間轉換為UTC時間
     *
     * @param time 時間
     * @param user 用戶
     *
     * @return UTC時間
     */
    private OffsetDateTime parseToUtc(String time, User user) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime localDateTime = LocalDateTime.parse(time, formatter);
        return localDateTime.atZone(ZoneId.of(user.getTimezone())).toOffsetDateTime();
    }
}

package xyz.dowob.stockweb.Repository.User;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.User.Todo;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author yuan
 * 用戶待辦事項與spring data jpa的資料庫操作介面
 * 繼承JpaRepository, 用於操作資料庫
 */
public interface TodoListRepository extends JpaRepository<Todo, Long> {
    /**
     * 透過用戶ID尋找用戶待辦事項
     *
     * @param userId 用戶ID
     *
     * @return 用戶待辦事項列表
     */
    List<Todo> findAllByUserId(Long userId);

    /**
     * 透過是否提醒與提醒時間在現在之後尋找用戶待辦事項
     *
     * @param isReminder 是否提醒
     * @param now        現在時間
     *
     * @return 用戶待辦事項列表
     */
    List<Todo> findAllByReminderAndReminderTimeIsAfter(boolean isReminder, OffsetDateTime now);
}

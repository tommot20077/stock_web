package xyz.dowob.stockweb.Repository.User;

import org.springframework.data.jpa.repository.JpaRepository;
import xyz.dowob.stockweb.Model.User.Todo;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author yuan
 */
public interface TodoListRepository extends JpaRepository<Todo, Long> {
    List<Todo> findAllByUserId(Long userId);
    List<Todo> findAllByReminderAndReminderTimeIsAfter(boolean isReminder, OffsetDateTime now);
}

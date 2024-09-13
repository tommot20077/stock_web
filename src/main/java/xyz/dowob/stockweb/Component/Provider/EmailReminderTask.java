package xyz.dowob.stockweb.Component.Provider;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import xyz.dowob.stockweb.Model.User.Todo;

/**
 * 這是一個用於發送待辦事項提醒郵件的方法。
 * 實現Runnable接口，用於在設置的定時器執行。
 *
 * @author yuan
 */
public class EmailReminderTask implements Runnable {
    private final Todo todo;

    private final JavaMailSender javaMailSender;

    private final String emailSender;

    /**
     * 這是一個構造函數，用於注入待辦事項、JavaMailSender和emailSender。
     *
     * @param todo           待辦事項
     * @param javaMailSender JavaMailSender
     * @param emailSender    發送郵件的地址
     */
    public EmailReminderTask(Todo todo, JavaMailSender javaMailSender, String emailSender) {
        this.todo = todo;
        this.javaMailSender = javaMailSender;
        this.emailSender = emailSender;
    }

    /**
     * 發送待辦事項提醒郵件, 並記錄日誌
     * 如果發生錯誤，記錄錯誤日誌
     */
    @Override
    public void run() {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailSender);
        message.setTo(todo.getUser().getEmail());
        message.setSubject("待辦事項提醒");
        message.setText("您的待辦事項：" + todo.getContent() + "\n已經到期，請盡快處理。");
        try {
            javaMailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("提醒郵件: " + todo.getId() + " 發送失敗", e);
        }
    }
}

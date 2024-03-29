package xyz.dowob.stockweb.Component.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import xyz.dowob.stockweb.Model.User.Token;
import xyz.dowob.stockweb.Model.User.User;
import xyz.dowob.stockweb.Repository.User.TokenRepository;
import xyz.dowob.stockweb.Service.User.UserService;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

@Component
public class MailTokenProvider {
    private final TokenRepository tokenRepository;
    private final JavaMailSender javaMailSender;

    Logger logger = LoggerFactory.getLogger(UserService.class);
    @Autowired
    public MailTokenProvider(TokenRepository tokenRepository, JavaMailSender javaMailSender) {
        this.tokenRepository = tokenRepository;
        this.javaMailSender = javaMailSender;
    }

    @Value(value = "${security.email.receive.url}")
    private String emailReceiveUrl;

    @Value(value = "${spring.mail.username}")
    private String emailSender;

    @Value(value = "${security.email.expiration}")
    private int expirationMinute;

    public void sendVerificationEmail(User user) throws RuntimeException {
        if (canSendEmail(user)) {
            String token = UUID.randomUUID().toString();
            String base64Token = Base64.getEncoder().encodeToString(token.getBytes());
            String base128Token = Base64.getEncoder().encodeToString(base64Token.getBytes());
            Token usertoken = user.getToken();
            usertoken.setEmailApiToken(base64Token);
            usertoken.setEmailApiTokenExpiryTime(OffsetDateTime.now().plusMinutes(expirationMinute));
            tokenRepository.save(usertoken);

            String verificationLink = emailReceiveUrl + base128Token;
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailSender);
            message.setTo(user.getEmail());
            message.setSubject("請驗證您的電子郵件");
            message.setText("請點擊以下鏈接完成驗證\n" + verificationLink + "\n(此鏈接在發送後"+ expirationMinute +"分鐘內有效)");
            javaMailSender.send(message);
            logger.info("發送驗證信到用戶" + user.getEmail());
        } else {
            throw new RuntimeException("目前已到達每小時發送電子郵件的限制，請稍後再試。");
        }
    }

    private synchronized boolean canSendEmail(User user){
        OffsetDateTime now = OffsetDateTime.now();
        Token usertoken = user.getToken();
        if(now.isAfter(usertoken.getEmailApiTokenResetTime())){
            usertoken.setEmailApiTokenResetTime(now.plusHours(1));
            usertoken.setEmailApiRequestCount(0);
        }
        boolean canSend = usertoken.getEmailApiRequestCount() < 3;
        if(canSend){
            usertoken.setEmailApiRequestCount(usertoken.getEmailApiRequestCount() + 1);
            tokenRepository.save(usertoken);
        }
        return canSend;
    }


    public User validateTokenAndReturnUser(String base128Token) {
        byte[] decodedBytes = Base64.getDecoder().decode(base128Token);
        String base64Token = new String(decodedBytes);

        Token usertoken = tokenRepository.findByEmailApiToken(base64Token).orElse(null);
        if (usertoken == null) {
            throw new RuntimeException("無效的密鑰");
        } else if (usertoken.getEmailApiTokenExpiryTime().isBefore(OffsetDateTime.now(ZoneId.of((usertoken.getUser().getTimezone()))))) {
            throw new RuntimeException("密鑰已過期");
        } else if ((usertoken.getEmailApiToken()).equals(base64Token)){
            return usertoken.getUser();
        } else {
            throw new RuntimeException("無效的密鑰");
        }
    }

}

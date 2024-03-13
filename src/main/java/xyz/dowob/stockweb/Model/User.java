package xyz.dowob.stockweb.Model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import xyz.dowob.stockweb.Enum.Role;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;

@Entity
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    private String rememberMeToken;

    private OffsetDateTime rememberMeTokenExpireTime;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime created;

    @Column(nullable = false)
    private OffsetDateTime updated;

    @Column(nullable = false, columnDefinition = "varchar(100) default 'Etc/UTC'")
    private String timezone = "Etc/UTC";

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role = Role.UNVERIFIED_USER;


    @PreUpdate
    protected void onUpdate() {
        updated = OffsetDateTime.now(ZoneId.of(timezone));
    }

    @PrePersist
    protected void onCreate() {
        created = OffsetDateTime.now(ZoneId.of(timezone));
        updated = OffsetDateTime.now(ZoneId.of(timezone));
    }

    public void createRememberMeToken(String token, int expireTimeDays) {
        setRememberMeToken(token);
        setRememberMeTokenExpireTime(OffsetDateTime.now(ZoneId.of(timezone)).plusDays(expireTimeDays));
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }



}

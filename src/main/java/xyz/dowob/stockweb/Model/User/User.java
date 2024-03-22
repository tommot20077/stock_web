package xyz.dowob.stockweb.Model.User;

import jakarta.persistence.*;
import lombok.Data;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import xyz.dowob.stockweb.Enum.Gender;
import xyz.dowob.stockweb.Enum.Role;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Entity
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String username;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;



    @Column(nullable = false, updatable = false)
    private OffsetDateTime created;

    @Column(nullable = false)
    private OffsetDateTime updated;

    @Column(nullable = false, columnDefinition = "varchar(100) default 'Etc/UTC'")
    private String timezone = "Etc/UTC";

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role = Role.UNVERIFIED_USER;

    @Column
    @Enumerated(EnumType.STRING)
    private Gender gender = Gender.OTHER;

    @OneToOne(cascade = CascadeType.ALL, mappedBy = "user")
    private Token token;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "user", orphanRemoval = true)
    private List<Subscribe> subscriptions = new ArrayList<>();;


    @PreUpdate
    protected void onUpdate() {
        updated = OffsetDateTime.now(ZoneId.of(timezone));
    }

    @PrePersist
    protected void onCreate() {
        created = OffsetDateTime.now(ZoneId.of(timezone));
        updated = OffsetDateTime.now(ZoneId.of(timezone));
    }



    public String extractUsernameFromEmail(String email) {
        if (email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        } else {
            throw new IllegalArgumentException("傳入的電子郵件不合法");
        }
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }


    @Override
    public String toString() {
        return (new ReflectionToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE) {
            @Override
            protected boolean accept(Field f) {
                return super.accept(f) && !f.getName().equals("token") && !f.getName().equals("subscriptions");
            }
        }).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

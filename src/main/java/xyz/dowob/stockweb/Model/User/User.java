package xyz.dowob.stockweb.Model.User;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.Data;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import xyz.dowob.stockweb.Enum.Gender;
import xyz.dowob.stockweb.Enum.Role;
import xyz.dowob.stockweb.Model.Currency.Currency;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * @author yuan
 * 使用者
 * 實現Serializable, 用於序列化
 * 1. id : 使用者編號
 * 2. username : 使用者名稱
 * 3. firstName : 名
 * 4. lastName : 姓
 * 5. password : 密碼
 * 6. email : 電子郵件
 * 7. created : 建立時間
 * 8. updated : 更新時間
 * 9. timezone : 時區
 * 10. preferredCurrency : 偏好貨幣
 * 11. role : 角色
 * 12. gender : 性別
 * 13. token : Token
 * 14. subscriptions : 訂閱
 * 15. property : 資產
 * 16. todoLists : 待辦事項
 */
@Entity
@Data
public class User implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false,
            unique = true)
    private String email;

    @Column(nullable = false,
            updatable = false)
    private OffsetDateTime created;

    @Column(nullable = false)
    private OffsetDateTime updated;

    @Column(nullable = false,
            columnDefinition = "varchar(100) default 'Etc/UTC'")
    private String timezone = "Etc/UTC";

    @ManyToOne(optional = false)
    @JoinColumn(name = "currency_id")
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
                      property = "id")
    private Currency preferredCurrency;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role = Role.UNVERIFIED_USER;

    @Column
    @Enumerated(EnumType.STRING)
    private Gender gender = Gender.OTHER;

    @OneToOne(cascade = CascadeType.ALL,
              mappedBy = "user")
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
                      property = "id")
    private Token token;

    @OneToMany(cascade = CascadeType.ALL,
               mappedBy = "user",
               orphanRemoval = true)
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
                      property = "id")
    private List<Subscribe> subscriptions = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL,
               mappedBy = "user",
               orphanRemoval = true)
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
                      property = "id")
    private List<Property> property = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL,
               mappedBy = "user",
               orphanRemoval = true)
    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class,
                      property = "id")
    private List<Todo> todoLists = new ArrayList<>();


    @PreUpdate
    protected void onUpdate() {
        updated = OffsetDateTime.now(ZoneId.of(timezone));
    }

    @PrePersist
    protected void onCreate() {
        created = OffsetDateTime.now(ZoneId.of(timezone));
        updated = OffsetDateTime.now(ZoneId.of(timezone));
    }


    /**
     * 從電子郵件中提取使用者名稱
     *
     * @param email 電子郵件
     *
     * @return 使用者名稱
     */
    public String extractUsernameFromEmail(String email) {
        if (email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        } else {
            throw new IllegalArgumentException("傳入的電子郵件不合法");
        }
    }

    /**
     * 取得使用者角色
     * 繼承GrantedAuthority
     *
     * @return 使用者角色
     */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }


    /**
     * 重寫toString
     * 隱藏token, subscriptions, property, todoLists, 避免循環參考
     *
     * @return 字串
     */
    @Override
    public String toString() {
        return (new ReflectionToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE) {
            @Override
            protected boolean accept(Field f) {
                return super.accept(f) && !"token".equals(f.getName()) && !"subscriptions".equals(f.getName()) && !"property".equals(f.getName()) && !"propertySummary".equals(
                        f.getName()) && !"todoLists".equals(f.getName());
            }
        }).toString();
    }

    /**
     * 重寫equals
     * 比較id
     *
     * @param o 物件
     *
     * @return 是否相同
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    /**
     * 重寫hashCode
     *
     * @return hashCode
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

package com.schoolsphere.people.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import com.schoolsphere.common.util.FieldCrypto;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "parent_profile")
public class ParentProfile extends TenantEntity {

    UUID userId;
    String firstName;
    String lastName;

    @Convert(converter = FieldCrypto.EncryptedStringConverter.class)
    String phoneEncrypted;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhoneEncrypted() {
        return phoneEncrypted;
    }

    public void setPhoneEncrypted(String phoneEncrypted) {
        this.phoneEncrypted = phoneEncrypted;
    }
}

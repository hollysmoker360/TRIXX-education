package com.schoolsphere.people.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import com.schoolsphere.common.util.FieldCrypto;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "student")
public class Student extends TenantEntity {

    UUID userId;
    String admissionNumber;
    String firstName;
    String lastName;
    String otherNames;
    LocalDate dateOfBirth;
    String gender;

    @Convert(converter = FieldCrypto.EncryptedStringConverter.class)
    String upiEncrypted;

    @Convert(converter = FieldCrypto.EncryptedStringConverter.class)
    String medicalNotesEncrypted;

    String photoKey;
    String status = "active";

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getAdmissionNumber() {
        return admissionNumber;
    }

    public void setAdmissionNumber(String admissionNumber) {
        this.admissionNumber = admissionNumber;
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

    public String getOtherNames() {
        return otherNames;
    }

    public void setOtherNames(String otherNames) {
        this.otherNames = otherNames;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getUpiEncrypted() {
        return upiEncrypted;
    }

    public void setUpiEncrypted(String upiEncrypted) {
        this.upiEncrypted = upiEncrypted;
    }

    public String getMedicalNotesEncrypted() {
        return medicalNotesEncrypted;
    }

    public void setMedicalNotesEncrypted(String medicalNotesEncrypted) {
        this.medicalNotesEncrypted = medicalNotesEncrypted;
    }

    public String getPhotoKey() {
        return photoKey;
    }

    public void setPhotoKey(String photoKey) {
        this.photoKey = photoKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

package com.msetransport.parcel.auth.user;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    private UUID id;
    private String username;
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    private Role role;
    private String merchantId;
    private UUID courierId;
    private boolean enabled;
    private Instant createdAt;

    protected UserAccount() {
    }

    public UserAccount(String username, String passwordHash, Role role, String merchantId, UUID courierId,
                       Instant createdAt) {
        if (role == Role.MERCHANT && merchantId == null) {
            throw new IllegalArgumentException("Merchant users need a merchantId");
        }
        if (role == Role.COURIER && courierId == null) {
            throw new IllegalArgumentException("Courier users need a courierId");
        }
        this.id = UUID.randomUUID();
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.merchantId = merchantId;
        this.courierId = courierId;
        this.enabled = true;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

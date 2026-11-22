package com.msetransport.parcel.dispatch.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "courier")
public class Courier {

    @Id
    private UUID id;
    private String name;
    private String phone;
    private String zone;
    private boolean active;

    protected Courier() {
    }

    public Courier(UUID id, String name, String phone, String zone, boolean active) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.zone = zone;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getZone() {
        return zone;
    }

    public boolean isActive() {
        return active;
    }
}

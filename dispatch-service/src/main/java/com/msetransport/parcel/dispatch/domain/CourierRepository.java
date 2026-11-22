package com.msetransport.parcel.dispatch.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourierRepository extends JpaRepository<Courier, UUID> {

    List<Courier> findByZoneAndActiveTrue(String zone);
}

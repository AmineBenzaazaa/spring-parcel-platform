package com.benzaazaa.parcel.auth.user;

public enum Role {
    /** Warehouse / operations staff: sees and manages everything. */
    OPS,
    /** Delivery driver: drives the status of deliveries assigned to them. */
    COURIER,
    /** E-commerce seller: creates and tracks their own COD orders. */
    MERCHANT
}

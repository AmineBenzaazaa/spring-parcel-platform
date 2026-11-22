package com.msetransport.parcel.support.web;

/** A request that is well-formed but violates a domain rule (e.g. illegal status transition). Maps to 409. */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

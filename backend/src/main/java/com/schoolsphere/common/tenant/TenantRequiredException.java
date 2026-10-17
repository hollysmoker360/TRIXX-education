package com.schoolsphere.common.tenant;

public class TenantRequiredException extends RuntimeException {

    public TenantRequiredException() {
        super("A school context is required for this operation. Select a school (X-School-Id) first.");
    }
}

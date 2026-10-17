package com.schoolsphere.common.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service (class) or method that requires a tenant context. The surrounding aspect opens
 * the transaction and sets the transaction-local tenant configuration before any statement runs,
 * so row-level security always sees a school id.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface TenantScoped {
}

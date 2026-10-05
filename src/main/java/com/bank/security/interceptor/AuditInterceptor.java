package com.bank.security.interceptor;

import com.bank.security.service.AuditService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.security.enterprise.SecurityContext;

import java.lang.reflect.Method;

@Audited
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class AuditInterceptor {

    @Inject
    private AuditService auditService;

    @Inject
    private SecurityContext securityContext;

    @AroundInvoke
    public Object auditMethod(InvocationContext context) throws Exception {
        Method method = context.getMethod();

        // 1. Resolve @Audited annotation accurately from method or target class
        Audited audited = method.getAnnotation(Audited.class);
        if (audited == null) {
            audited = context.getTarget().getClass().getAnnotation(Audited.class);
        }

        String action = (audited != null && !audited.action().isEmpty())
                ? audited.action()
                : method.getName();

        // 2. Safe Principal Extraction
        String principal = "UNKNOWN";
        if (securityContext != null && securityContext.getCallerPrincipal() != null) {
            principal = securityContext.getCallerPrincipal().getName();
        }

        // 3. Robust Target Class Name Resolution (Handles Javassist & Weld proxies)
        Class<?> targetClass = context.getTarget().getClass();
        String entityName = targetClass.getSimpleName();
        if (entityName.contains("$") || entityName.contains("_Subclass")) {
            entityName = targetClass.getSuperclass().getSimpleName();
        }

        Object result;
        try {
            result = context.proceed();

            // Record successful execution
            if (auditService != null) {
                auditService.recordAudit(principal, action, entityName, null, "127.0.0.1", "SUCCESS", null);
            }
        } catch (Exception e) {
            // Record failed execution before rethrowing
            if (auditService != null) {
                auditService.recordAudit(principal, action, entityName, null, "127.0.0.1", "FAILED", e.getMessage());
            }
            throw e;
        }

        return result;
    }
}
package com.bank.security.service;

import com.bank.security.interceptor.Audited;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AuditedTestBean {

    @Audited(action = "EXECUTE_SENSITIVE_ACTION")
    public String executeAction(String param) {
        return "RESULT_" + param;
    }
}
package com.jobwork.config;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class SpringContext {

    private static ApplicationContext context;

    public SpringContext(ApplicationContext context) {
        SpringContext.context = context;
    }

    public static ApplicationContext getApplicationContext() {
        return context;
    }
}
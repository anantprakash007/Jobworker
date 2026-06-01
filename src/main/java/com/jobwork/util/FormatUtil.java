package com.jobwork.util;

import java.math.BigDecimal;

public class FormatUtil {

    public static String money(BigDecimal v) {
        return "₹ " + String.format("%.2f",
                v == null ? BigDecimal.ZERO : v);
    }
}
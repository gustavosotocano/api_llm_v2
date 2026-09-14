package com.enterprise.agentapi.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentOccurrence(LocalDate date, BigDecimal amount, String currency) {}

package com.cashflow.auth;
import java.io.Serializable;
import java.util.UUID;
public record SessionUser(UUID id,int authVersion) implements Serializable {}

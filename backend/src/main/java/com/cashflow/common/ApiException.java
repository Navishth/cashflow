package com.cashflow.common;
public class ApiException extends RuntimeException {
    public final int status;
    public ApiException(int status,String message) { super(message); this.status=status; }
    public static ApiException notFound() { return new ApiException(404,"Record not found."); }
    public static void require(boolean condition,String message) { if(!condition) throw new ApiException(400,message); }
}

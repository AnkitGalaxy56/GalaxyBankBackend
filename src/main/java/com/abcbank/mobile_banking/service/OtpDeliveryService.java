package com.abcbank.mobile_banking.service;

public interface OtpDeliveryService {
    void sendOtp(String mobileNumber, String otp);
}

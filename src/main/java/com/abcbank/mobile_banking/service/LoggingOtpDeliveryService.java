 
package com.abcbank.mobile_banking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoggingOtpDeliveryService implements OtpDeliveryService {


    private static final Logger log =
            LoggerFactory.getLogger(LoggingOtpDeliveryService.class);

    @Override
    public void sendOtp(String mobileNumber, String otp) {
        // LOCAL DEVELOPMENT ONLY. Never log OTPs in production.
        log.warn(
            "LOCAL TEST OTP for mobile ending {}: {}",
            mobileNumber.substring(
                Math.max(0, mobileNumber.length() - 4)
            ),
            otp
        );
    }
}

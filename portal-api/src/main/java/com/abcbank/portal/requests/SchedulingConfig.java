package com.abcbank.portal.requests;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables @Scheduled jobs (used to deliver scheduled push notifications). */
@Configuration
@EnableScheduling
public class SchedulingConfig {}

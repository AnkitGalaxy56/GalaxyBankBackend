package com.abcbank.mobile_banking.service;

import com.abcbank.mobile_banking.dto.request.FundTransferRequest;
import com.abcbank.mobile_banking.dto.response.FundTransferResponse;
import com.abcbank.mobile_banking.entity.FundTransferRequestEntity;
import com.abcbank.mobile_banking.repository.FundTransferRequestRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.Map;

@Service
public class FundTransferService {
 private final JdbcTemplate jdbc; private final FundTransferRequestRepository repository;
 public FundTransferService(JdbcTemplate jdbc,FundTransferRequestRepository repository){this.jdbc=jdbc;this.repository=repository;}

 @Transactional
 public FundTransferResponse submit(String customerId,FundTransferRequest r){
  if(!r.accountNo().equals(r.reConfirmAccountNo())) throw new IllegalArgumentException("Beneficiary account numbers do not match.");
  String mode=r.paymentMode().toUpperCase(Locale.ROOT);
  if("RTGS".equals(mode) && r.amount().compareTo(new java.math.BigDecimal("200000.00"))<0)
   throw new IllegalArgumentException("RTGS amount must be at least Rs. 2,00,000. Please select NEFT or IMPS.");
  Map<String,Object> debit=jdbc.queryForMap("""
   SELECT a.account_no,a.account_status,a.account_type,a.branch_code
   FROM public."MB002003" a WHERE a.account_no=? AND a.customer_no=?
   """,r.debitAccountId(),customerId);
  if(!"ACTIVE".equalsIgnoreCase(String.valueOf(debit.get("account_status"))))
   throw new IllegalArgumentException("Debit account is not active.");
  Map<String,Object> user=jdbc.queryForMap("""
   SELECT user_id FROM public."MB001001" WHERE customer_no=? AND user_type='CU' AND status='ACTIVE'
   """,customerId);
  Map<String,Object> beneficiaryBranch=jdbc.queryForMap("""
   SELECT b.bank_code,b.branch_name FROM public."MB002002" b WHERE upper(b.ifsc_code)=upper(?)
   """,r.ifscCode());
  String bankName=jdbc.queryForObject("""
   SELECT bank_name FROM public."MB002001" WHERE bank_code=?
   """,String.class,beneficiaryBranch.get("bank_code"));
  FundTransferRequestEntity saved=repository.save(new FundTransferRequestEntity(
   String.valueOf(debit.get("branch_code")),customerId,String.valueOf(debit.get("account_no")),
   String.valueOf(debit.get("account_status")),String.valueOf(debit.get("account_type")),mode,
   r.beneficiaryName().trim(),r.accountNo().trim(),bankName,r.ifscCode().toUpperCase(Locale.ROOT),
   r.amount(),r.remarks(),((Number)user.get("user_id")).longValue()));
  return new FundTransferResponse(saved.getId(),saved.getReqStatus(),mode,r.amount(),saved.getCreatedOn(),
   "Transfer request submitted for bank processing.");
 }
}

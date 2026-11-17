package com.abcbank.portal.requests;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Registry of every request-handling module: which table it uses and exactly which
 * columns a client may read or write. Nothing outside this registry can be written,
 * so a client can never choose a column name (no mass assignment).
 *
 * Each field lists UI aliases: the key names the web pages already use. On read, the
 * value is returned under every alias; on write, the first alias present is used.
 * Columns that do not exist in the database yet are skipped automatically
 * (see sql/02_request_handling_additions.sql for the optional ones).
 */
public final class RequestModules {

    public enum Type { STR, DEC, INT, DATE, BOOL }

    /** How the customer / branch / account of a new request are determined (always server-side). */
    public enum Resolve {
        /** The client sends an account number; customer and branch come from the account master (MB002003). */
        ACCOUNT,
        /** The client sends a customer number; the account and branch are looked up. */
        CUSTOMER,
        /** Customer number only (loan leads): the table has no account column. */
        CUSTOMER_ONLY
    }

    public static final Pattern IFSC = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
    public static final Pattern MOBILE = Pattern.compile("^[6-9][0-9]{9}$");
    public static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");
    public static final Pattern ACCOUNT = Pattern.compile("^[A-Za-z0-9\\-]{4,30}$");

    public static final class Field {
        public final String column;
        public final Type type;
        public final List<String> aliases;
        public boolean required, mask, readOnly, identity, upper, owned;
        public int max = 255;
        public int scale = 2;
        public BigDecimal min;
        public Pattern pattern;
        public String message;
        public List<String> allowed;
        public String defaultValue;
        public String label;

        Field(String column, Type type, String... aliases) {
            this.column = column;
            this.type = type;
            this.aliases = List.of(aliases);
        }

        public Field req() { required = true; return this; }
        public Field mask() { mask = true; return this; }
        public Field ro() { readOnly = true; return this; }
        /** Identity fields (account / customer) are set at creation and can never be edited. */
        public Field id() { identity = true; return this; }
        public Field upper() { upper = true; return this; }
        /** The value is an account number that must exist in the account master for the same customer. */
        public Field owned() { owned = true; return this; }
        public Field max(int n) { max = n; return this; }
        public Field scale(int n) { scale = n; return this; }
        public Field min(String v) { min = new BigDecimal(v); return this; }
        public Field re(Pattern p, String msg) { pattern = p; message = msg; return this; }
        public Field oneOf(String... v) { allowed = List.of(v); return this; }
        public Field dflt(String v) { defaultValue = v; return this; }
        public Field label(String v) { label = v; return this; }
    }

    public record Module(String key, String table, String prefix, Resolve resolve,
                         String statusCol, String typeCol, String identityColumn,
                         List<String> nameAliases, List<Field> fields) {
        public String qualified() { return "public.\"" + table + "\""; }
    }

    private static Field str(String c, String... a) { return new Field(c, Type.STR, a); }
    private static Field dec(String c, String... a) { return new Field(c, Type.DEC, a); }
    private static Field integer(String c, String... a) { return new Field(c, Type.INT, a); }
    private static Field date(String c, String... a) { return new Field(c, Type.DATE, a); }
    private static Field bool(String c, String... a) { return new Field(c, Type.BOOL, a); }

    private static final Map<String, Module> MODULES = new LinkedHashMap<>();

    private static void add(Module m, String... keys) {
        for (String k : keys) MODULES.put(k, m);
    }

    static {
        // ---- MB004001 Remittance (RTGS / NEFT / IMPS) -------------------------------------------
        Module pay = new Module("rtgs-neft-imps", "MB004001", "PAY", Resolve.ACCOUNT, "account_status", "account_type", "account_no",
                List.of("customer", "customerName", "name"),
                List.of(
                        str("account_no", "account", "accountNo").req().id().mask().re(ACCOUNT, "Enter a valid account number."),
                        str("pay_mode", "mode", "payMode").req().oneOf("NEFT", "RTGS", "IMPS").upper(),
                        str("ben_name", "beneficiary", "beneficiaryName").req().max(150),
                        str("ben_account", "beneficiaryAccount").req().mask().max(34).re(Pattern.compile("^[A-Za-z0-9]{6,34}$"), "Enter a valid beneficiary account number."),
                        str("ben_bank", "bank", "beneficiaryBank").max(150),
                        str("ben_ifsc", "ifsc", "beneficiaryIfsc").req().upper().max(11).re(IFSC, "Enter a valid IFSC code (e.g. HDFC0001234)."),
                        dec("amount", "amount").req().min("0.01"),
                        str("remarks", "remarks").max(255),
                        integer("is_sys", "isSys").ro(),
                        str("account_status", "accountStatus", "debitStatus").ro()));
        add(pay, "rtgs-neft-imps", "payments");

        // ---- MB004002 Cheque book --------------------------------------------------------------
        Module chq = new Module("cheque-book", "MB004002", "CHQ", Resolve.ACCOUNT, "account_status", "account_type", "account_no",
                List.of("customerName", "customer", "name"),
                List.of(
                        str("account_no", "accountNumber", "account", "accountNo").req().id().mask().re(ACCOUNT, "Enter a valid account number."),
                        integer("noof_books", "noofBooks", "numberOfBooks").min("1").dflt("1"),
                        str("delivery", "deliveryMethod", "delivery").req().max(20).dflt("Collect from branch"),
                        str("cheque_book_type", "chequeBookType").max(20),
                        str("delivery_address", "deliveryAddress").max(255),
                        str("account_status", "accountStatus", "statusCode").ro()));
        add(chq, "cheque-book");

        // ---- MB004003 Stop payment -------------------------------------------------------------
        Module stop = new Module("stop-payment", "MB004003", "STP", Resolve.ACCOUNT, "account_status", "account_type", "account_no",
                List.of("customer", "customerName", "name"),
                List.of(
                        str("account_no", "debitAccount", "accountNo").req().id().mask().re(ACCOUNT, "Enter a valid account number."),
                        str("cheque_no", "cheque", "chequeNo").req().max(20).re(Pattern.compile("^[A-Za-z0-9]{1,20}$"), "Cheque number may contain letters and digits only."),
                        date("cheque_date", "chequeDate"),
                        str("issued_to", "issuedTo").max(150),
                        dec("cheque_amount", "amount").min("0.01"),
                        str("reason", "reason").req().max(255),
                        str("account_status", "accountStatus").ro()));
        add(stop, "stop-payment");

        // ---- MB004004 FD opening / MB004005 RD opening ------------------------------------------
        List<Field> depositFields = new ArrayList<>(List.of(
                str("account_no", "debitAccountId", "debitAccount").req().id().mask().re(ACCOUNT, "Enter a valid account number."),
                str("prod_code", "productCode", "product").req().max(20),
                str("prod_name", "productName").max(100),
                str("scheme_code", "schemeCode").req().max(20),
                str("scheme_name", "schemeName").max(100),
                date("cert_date", "certificateDate"),
                date("asof_date", "asOfDate"),
                dec("deposit_amount", "depositAmount", "deposit").req().min("0.01"),
                dec("int_rate", "interestRate", "rate").req().min("0").scale(4),
                dec("offset_rate", "offsetRate").min("0").scale(4),
                integer("tenure", "tenure").req().min("1"),
                bool("auto_renewal", "autoRenewal"),
                str("cr_int_acid", "interestCreditAccount").max(30),
                str("maturity_acid", "maturityCreditAccount").max(30),
                date("maturity_date", "maturityDate").ro(),
                dec("maturity_value", "maturityValue").ro(),
                str("debit_account_status", "accountStatus", "debitStatus").ro()));
        List<Field> fdFields = new ArrayList<>(depositFields);
        fdFields.add(bool("renew_int", "renewWithInterest"));
        Module fd = new Module("fd-opening", "MB004004", "FD", Resolve.ACCOUNT, "debit_account_status", "debit_account_type", "account_no",
                List.of("name", "customer", "customerName"), fdFields);
        add(fd, "fd-opening", "opening-accounts");
        Module rd = new Module("rd-opening", "MB004005", "RD", Resolve.ACCOUNT, "debit_account_status", "debit_account_type", "account_no",
                List.of("name", "customer", "customerName"), depositFields);
        add(rd, "rd-opening");

        // ---- MB004006 Loan EMI payment ---------------------------------------------------------
        Module emi = new Module("loan-emi-payment", "MB004006", "EMI", Resolve.ACCOUNT, "account_status", "account_type", "account_no",
                List.of("debitName", "customer", "customerName", "name"),
                List.of(
                        str("account_no", "debitAccount", "accountNo").req().id().mask().re(ACCOUNT, "Enter a valid account number."),
                        str("loan_account_no", "creditAccount", "loanAccount").req().owned().mask().re(ACCOUNT, "Enter a valid loan account number."),
                        str("loan_ac_status", "creditStatus").max(20),
                        str("loan_ac_name", "creditName").max(150),
                        dec("emi_amount", "debitAmount", "amount").req().min("0.01"),
                        dec("total_outstanding", "outstanding").min("0"),
                        dec("int_outstanding", "interestOutstanding").min("0"),
                        date("expiry_date", "expiryDate"),
                        dec("instal_amount", "instalmentAmount"),
                        str("account_status", "debitStatus", "accountStatus").ro()));
        add(emi, "loan-emi-payment");

        // ---- MB004007 FD / RD closing ----------------------------------------------------------
        Module close = new Module("closing-accounts", "MB004007", "FDC", Resolve.ACCOUNT, "account_status", "account_type", "account_no",
                List.of("name", "customer", "customerName", "creditName"),
                List.of(
                        str("account_no", "creditAccountId", "creditAccount", "accountNo").req().id().mask().re(ACCOUNT, "Enter a valid credit account number."),
                        str("fd_acid", "debitAccountId", "account", "debitAccount").req().owned().mask().max(30).re(ACCOUNT, "Enter a valid deposit account number."),
                        date("cert_date", "certificateDate"),
                        date("asof_date", "asOfDate"),
                        dec("deposit_amount", "depositAmount", "deposit").min("0.01"),
                        dec("int_rate", "interestRate").min("0").scale(4),
                        dec("offset_rate", "offsetRate").min("0").scale(4),
                        integer("tenure", "tenure").min("1"),
                        dec("maturity_value", "maturityValue"),
                        date("maturity_date", "maturityDate"),
                        str("remarks", "reason", "remarks").max(255),
                        str("account_status", "creditStatus").ro()));
        add(close, "closing-accounts");

        // ---- MB004008 Standing instruction -----------------------------------------------------
        Module si = new Module("standing-instructions", "MB004008", "SI", Resolve.ACCOUNT, "account_status", "account_type", "account_no",
                List.of("debitName", "customer", "customerName", "name"),
                List.of(
                        str("account_no", "debitAccount", "accountNo").req().id().mask().re(ACCOUNT, "Enter a valid account number."),
                        dec("debit_amount", "debitAmount", "amount").req().min("0.01"),
                        integer("tenure", "tenure").req().min("1"),
                        str("frequency", "frequency").req().oneOf("Daily", "Weekly", "Monthly", "Quarterly", "Half-yearly", "Yearly"),
                        date("expiry_date", "expiryDate", "expiry"),
                        str("cr_acid", "creditAccount").req().mask().re(ACCOUNT, "Enter a valid credit account number."),
                        str("cr_ac_status", "creditStatus").max(20),
                        str("cr_ac_name", "creditName").max(150),
                        str("account_status", "debitStatus", "accountStatus").ro()));
        add(si, "standing-instructions");

        // ---- MB004009 Locker application -------------------------------------------------------
        Module locker = new Module("locker-application", "MB004009", "LKR", Resolve.CUSTOMER, "account_status", "account_type", "customer_no",
                List.of("customerName", "customer", "name"),
                List.of(
                        str("customer_no", "customerNo").req().id().max(30).re(ACCOUNT, "Enter a valid customer number."),
                        str("mobile", "mobileNo", "mobile").req().re(MOBILE, "Enter a valid 10-digit mobile number."),
                        str("email", "email").req().max(254).re(EMAIL, "Enter a valid email address."),
                        integer("operation_yrs", "operationYears").min("1"),
                        str("locker_type", "lockerType").max(20).oneOf("Small", "Medium", "Large"),
                        str("operation_mode", "operationMode").max(30).oneOf("Single", "Joint", "Either or Survivor"),
                        str("nominee_name", "nomineeName").max(150),
                        str("account_status", "accountStatus").ro()));
        add(locker, "locker-application");

        // ---- MB004010 Loan application / lead --------------------------------------------------
        Module loan = new Module("fresh-loan-application", "MB004010", "LN", Resolve.CUSTOMER_ONLY, null, null, "customer_no",
                List.of("customerName", "customer", "name"),
                List.of(
                        str("customer_no", "customerNo").req().id().max(30).re(ACCOUNT, "Enter a valid customer number."),
                        str("mobile", "mobileNo", "mobile").re(MOBILE, "Enter a valid 10-digit mobile number."),
                        str("email", "email").max(254).re(EMAIL, "Enter a valid email address."),
                        str("product_code", "productType", "product").req().max(20),
                        dec("loan_amount", "loanAmount", "amount").req().min("0.01"),
                        dec("total_income", "totalIncome", "income").min("0"),
                        dec("total_exp", "totalExpense").min("0"),
                        str("eligibility", "eligibility").ro()));
        add(loan, "fresh-loan-application", "lead-management");
    }

    private RequestModules() {}

    public static Module find(String key) {
        String k = key == null ? "" : key.trim();
        if (k.startsWith("validation/")) k = k.substring("validation/".length());
        return MODULES.get(k);
    }

    public static Collection<Module> all() {
        return new LinkedHashSet<>(MODULES.values());
    }
}

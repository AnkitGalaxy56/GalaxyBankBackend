
package com.abcbank.mobile_banking.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.abcbank.mobile_banking.repository.KycRepository;

@Service
public class KycService {

    private final KycRepository repository;

    @Value("${app.kyc.storage-location:uploads/kyc}")
    private String storageLocation;

    @Value("${app.kyc.max-file-size-bytes:5242880}")
    private long maxFileSize;

    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of(
            "AADHAAR_FRONT",
            "AADHAAR_BACK",
            "ADDRESS_PROOF",
            "SELFIE"
    );

    private static final Set<String> REQUIRED_DOCUMENT_TYPES = Set.of(
            "AADHAAR_FRONT",
            "AADHAAR_BACK",
            "ADDRESS_PROOF",
            "SELFIE"
    );

    public KycService(KycRepository repository) {
        this.repository = repository;
    }

    private void validateAccount(String customerNo, String accountNo) {
        try {
            repository.findAccount(customerNo, accountNo);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    private Long getCustomerUserId(String customerNo) {
        try {
            return repository.findCustomerUserId(customerNo);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
    }

    private String getExtension(String filename) {

        if (filename == null || !filename.contains(".")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File must have JPG, JPEG or PNG extension"
            );
        }

        String extension = filename.substring(filename.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);

        if (!Set.of("jpg", "jpeg", "png").contains(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPG, JPEG and PNG files are allowed"
            );
        }

        return extension;
    }

    private void validateFileContent(byte[] bytes, String extension) {

        boolean jpeg = bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;

        boolean png = bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;

        if ("png".equals(extension) && !png) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The uploaded file is not a valid PNG image"
            );
        }

        if (("jpg".equals(extension) || "jpeg".equals(extension)) && !jpeg) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The uploaded file is not a valid JPEG image"
            );
        }
    }

    @Transactional
    public Map<String, Object> uploadDocument(
            String customerNo,
            String accountNo,
            String documentType,
            MultipartFile file) {

        validateAccount(customerNo, accountNo);

        if (documentType == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Document type is required"
            );
        }

        documentType = documentType.trim().toUpperCase(Locale.ROOT);

        if (!ALLOWED_DOCUMENT_TYPES.contains(documentType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid document type. Allowed: " + REQUIRED_DOCUMENT_TYPES
            );
        }

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please select a file to upload"
            );
        }

        if (file.getSize() > maxFileSize) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum file size allowed is 5 MB"
            );
        }

        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null || originalFileName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Original file name is missing"
            );
        }

        String extension = getExtension(originalFileName);

        byte[] bytes;

        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unable to read uploaded file"
            );
        }

        validateFileContent(bytes, extension);

        Long userId = getCustomerUserId(customerNo);

        String storedFileName = UUID.randomUUID() + "." + extension;

        Path directory = Path.of(storageLocation).toAbsolutePath().normalize();
        Path target = directory.resolve(storedFileName).normalize();

        if (!target.startsWith(directory)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid file path"
            );
        }

        boolean fileStored = false;

        try {
            Files.createDirectories(directory);
            Files.write(target, bytes);
            fileStored = true;

            repository.saveDocument(
                    customerNo,
                    accountNo,
                    documentType,
                    documentType,
                    originalFileName,
                    storedFileName,
                    target.toString(),
                    extension,
                    file.getSize(),
                    userId
            );

            repository.ensureProgress(customerNo, accountNo);

            int uploadedCount = repository.countRequiredDocuments(
                    customerNo, accountNo
            );

            boolean uploadCompleted = uploadedCount == REQUIRED_DOCUMENT_TYPES.size();

            repository.updateUploadProgress(
                    customerNo,
                    accountNo,
                    uploadCompleted
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "KYC document uploaded successfully");
            response.put("documentType", documentType);
            response.put("storedFileName", storedFileName);
            response.put("fileSize", file.getSize());
            response.put("uploadedDocuments", uploadedCount);
            response.put("requiredDocuments", REQUIRED_DOCUMENT_TYPES.size());
            response.put("uploadStepCompleted", uploadCompleted);

            return response;

        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to store KYC document"
            );
        } catch (RuntimeException ex) {
            if (fileStored) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                    // Database operation failed; file cleanup was attempted.
                }
            }
            throw ex;
        }
    }

    public Map<String, Object> getDocuments(
            String customerNo,
            String accountNo) {

        validateAccount(customerNo, accountNo);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("documents", repository.findDocuments(customerNo, accountNo));

        return response;
    }

    public Map<String, Object> getProgress(
            String customerNo,
            String accountNo) {

        validateAccount(customerNo, accountNo);

        repository.ensureProgress(customerNo, accountNo);

        int uploadedCount = repository.countRequiredDocuments(
                customerNo, accountNo
        );

        boolean completed = uploadedCount == REQUIRED_DOCUMENT_TYPES.size();

        repository.updateUploadProgress(customerNo, accountNo, completed);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("uploadedDocuments", uploadedCount);
        response.put("requiredDocuments", REQUIRED_DOCUMENT_TYPES.size());
        response.put("progress", repository.findProgress(customerNo, accountNo));

        return response;
    }

    @Transactional
    public Map<String, Object> verifyDetails(
            String customerNo,
            String accountNo) {

        validateAccount(customerNo, accountNo);

        repository.ensureProgress(customerNo, accountNo);

        int uploadedCount = repository.countRequiredDocuments(
                customerNo, accountNo
        );

        if (uploadedCount != REQUIRED_DOCUMENT_TYPES.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please upload all four required KYC documents before verifying details"
            );
        }

        repository.updateUploadProgress(customerNo, accountNo, true);

        int updated = repository.verifyDetails(customerNo, accountNo);

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "KYC details cannot be verified in the current status"
            );
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "KYC details verified successfully");
        response.put("currentStep", 3);

        return response;
    }

    @Transactional
    public Map<String, Object> submitKyc(
            String customerNo,
            String accountNo) {

        validateAccount(customerNo, accountNo);

        int updated = repository.submitKyc(customerNo, accountNo);

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Complete document upload and details verification before submitting KYC"
            );
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "KYC submitted successfully for review");
        response.put("kycStatus", "PENDING_REVIEW");
        response.put("currentStep", 3);

        return response;
    }
}

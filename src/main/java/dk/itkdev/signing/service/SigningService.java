package dk.itkdev.signing.service;

import dk.gov.nemlogin.signing.dto.SigningPayloadDTO;
import dk.gov.nemlogin.signing.format.SignatureFormat;
import dk.gov.nemlogin.signing.model.FlowType;
import dk.gov.nemlogin.signing.model.Language;
import dk.gov.nemlogin.signing.model.SignatureKeys;
import dk.gov.nemlogin.signing.model.SignatureParameters;
import dk.gov.nemlogin.signing.model.SignersDocument;
import dk.gov.nemlogin.signing.model.SignersDocumentFile;
import dk.gov.nemlogin.signing.service.SigningPayloadService;
import dk.gov.nemlogin.signing.service.TransformationContext;
import dk.gov.nemlogin.signing.validation.model.SignatureValidationContext;
import dk.gov.nemlogin.signing.validation.model.ValidationReport;
import dk.gov.nemlogin.signing.validation.service.SignatureValidationService;
import dk.itkdev.signing.config.ItkdevProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class SigningService {

    private static final Logger LOG = LoggerFactory.getLogger(SigningService.class);

    private final SigningPayloadService signingPayloadService;
    private final SignatureKeys signatureKeys;
    private final String signingClientUrl;
    private final String entityID;
    private final ItkdevProperties properties;
    private final RestTemplate restTemplate;
    private final SignatureValidationService signatureValidationService;
    private final SignatureValidationContext.Builder signatureValidationContextBuilder;

    public SigningService(
            SigningPayloadService signingPayloadService,
            SignatureKeys signatureKeys,
            @Qualifier("signingClientUrl") String signingClientUrl,
            @Qualifier("entityID") String entityID,
            ItkdevProperties properties,
            SignatureValidationService signatureValidationService,
            SignatureValidationContext.Builder signatureValidationContextBuilder) {
        this.signingPayloadService = signingPayloadService;
        this.signatureKeys = signatureKeys;
        this.signingClientUrl = signingClientUrl;
        this.entityID = entityID;
        this.properties = properties;
        this.restTemplate = new RestTemplate();
        this.signatureValidationService = signatureValidationService;
        this.signatureValidationContextBuilder = signatureValidationContextBuilder;
    }

    public String getSigningClientUrl() {
        return signingClientUrl;
    }

    /**
     * Generate a new correlation ID.
     */
    public String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Fetch a PDF from a base64-encoded URI, validate it, and save locally.
     *
     * @param base64Uri the base64-encoded PDF URL
     * @return the unique local filename (e.g. "91d56055f7274fdeb327077d1e32e5d1.pdf")
     */
    public String fetchAndPrepareDocument(String base64Uri) throws IOException {
        String filename = new String(Base64.getDecoder().decode(base64Uri));

        if (filename.isEmpty() || !filename.matches("^https?://.*\\.pdf$")) {
            throw new IllegalArgumentException("Invalid filename given (" + filename + ").");
        }

        if (!isValidDomain(filename)) {
            throw new SecurityException("Invalid url for external file: " + filename + ". Valid URLs must be defined in configuration");
        }

        // Generate unique local name: md5(url + nanoTime + uuid) + ".pdf"
        String unique = filename + System.nanoTime() + UUID.randomUUID().toString();
        String hash = md5(unique);
        String localFilename = hash + ".pdf";

        Path sourceDir = Paths.get(properties.getSourceDocumentsDir());
        Files.createDirectories(sourceDir);
        Path targetPath = sourceDir.resolve(localFilename);

        debug("Fetching {} to {}", filename, targetPath);

        byte[] pdfData = restTemplate.getForObject(filename, byte[].class);
        if (pdfData == null || pdfData.length == 0) {
            throw new IOException("Failed to fetch PDF from: " + filename);
        }
        Files.write(targetPath, pdfData);

        return localFilename;
    }

    /**
     * Save an uploaded PDF document to the source documents directory.
     *
     * @param pdfData the raw PDF bytes
     * @return the unique local filename (e.g. "91d56055f7274fdeb327077d1e32e5d1.pdf")
     */
    public String saveUploadedDocument(byte[] pdfData) throws IOException {
        if (pdfData == null || pdfData.length == 0) {
            throw new IllegalArgumentException("PDF data must not be empty.");
        }

        String unique = "upload" + System.nanoTime() + UUID.randomUUID().toString();
        String hash = md5(unique);
        String localFilename = hash + ".pdf";

        Path sourceDir = Paths.get(properties.getSourceDocumentsDir());
        Files.createDirectories(sourceDir);
        Path targetPath = sourceDir.resolve(localFilename);

        Files.write(targetPath, pdfData);
        debug("Saved uploaded document: {}", targetPath);

        return localFilename;
    }

    /**
     * Generate a signing payload for a locally stored PDF.
     */
    public SigningPayloadDTO generateSigningPayload(String filename) throws Exception {
        Path pdfPath = Paths.get(properties.getSourceDocumentsDir()).resolve(filename);
        byte[] pdfData = Files.readAllBytes(pdfPath);

        // Create PdfSignersDocument
        SignersDocumentFile file = SignersDocumentFile.builder()
                .setData(pdfData)
                .setName(filename)
                .build();
        SignersDocument.PdfSignersDocument document = new SignersDocument.PdfSignersDocument(file);

        // Build signature parameters
        SignatureParameters signatureParameters = SignatureParameters.builder()
                .setFlowType(FlowType.ServiceProvider)
                .setEntityID(entityID)
                .setPreferredLanguage(Language.valueOf(Locale.ENGLISH))
                .setReferenceText(filename)
                .setDocumentFormat(document.getFormat())
                .setSignatureFormat(SignatureFormat.PAdES)
                .build();

        // Create transformation context and produce signing payload
        TransformationContext ctx = new TransformationContext(document, signatureKeys, signatureParameters);
        return signingPayloadService.produceSigningPayloadDTO(ctx);
    }

    /**
     * Validate that a given hash matches SHA-1(salt + value).
     */
    public boolean validateHash(String expectedHash, String value) {
        String salt = properties.getHashSalt();
        if (salt == null || salt.isEmpty()) {
            LOG.error("Signing hash salt not set. Please set it in the configuration.");
            return false;
        }

        try {
            byte[] computed = sha1((salt + value).getBytes());
            String computedHex = bytesToHex(computed);
            return MessageDigest.isEqual(
                    expectedHash.getBytes(),
                    computedHex.getBytes()
            );
        } catch (NoSuchAlgorithmException e) {
            LOG.error("SHA-1 algorithm not available", e);
            return false;
        }
    }

    /**
     * Check if a URL belongs to the allowed domains.
     */
    public boolean isValidDomain(String url) {
        try {
            String host = new URI(url).getHost();
            var allowedDomains = properties.getAllowedDomains();

            if (allowedDomains == null || allowedDomains.isEmpty()) {
                LOG.warn("List of allowed domains is empty. It is recommended to provide it. Allowing request from {}", url);
                return true;
            }

            return allowedDomains.contains(host);
        } catch (Exception e) {
            LOG.error("Failed to parse URL: {}", url, e);
            return false;
        }
    }

    /**
     * Save a signed document (base64-encoded) to the signed documents directory.
     *
     * @param filename the original filename (e.g. "91d56055f7274fdeb327077d1e32e5d1.pdf")
     * @param base64Result the base64-encoded signed PDF
     */
    public void saveSignedDocument(String filename, String base64Result) throws IOException {
        String hash = filename.replace(".pdf", "");
        String signedFilename = hash + "-signed.pdf";

        Path signedDir = Paths.get(properties.getSignedDocumentsDir());
        Files.createDirectories(signedDir);
        Path targetPath = signedDir.resolve(signedFilename);

        byte[] data = Base64.getDecoder().decode(base64Result);
        Files.write(targetPath, data);

        debug("Saved signed document: {}", targetPath);
    }

    /**
     * Get the path to a signed document, validating the filename format.
     */
    public Path getSignedDocumentPath(String file) {
        if (!file.matches("^[a-z0-9]{32}\\.pdf$")) {
            throw new IllegalArgumentException(
                    "Invalid file name: " + file + ". Must be exactly 32 letters or numbers followed by '.pdf'");
        }

        String hash = file.substring(0, 32);
        return Paths.get(properties.getSignedDocumentsDir()).resolve(hash + "-signed.pdf");
    }

    /**
     * Validate a signed document by calling the NemLog-In Signature Validation API.
     *
     * @param file the original filename (e.g. "91d56055f7274fdeb327077d1e32e5d1.pdf")
     * @return the validation report
     */
    public ValidationReport validateSignedDocument(String file) throws IOException {
        Path signedPath = getSignedDocumentPath(file);
        byte[] pdfData = Files.readAllBytes(signedPath);

        String signedFilename = file.replace(".pdf", "") + "-signed.pdf";

        SignatureValidationContext ctx = signatureValidationContextBuilder.copy()
                .setDocumentName(signedFilename)
                .setDocumentData(pdfData)
                .build();

        return signatureValidationService.validate(ctx);
    }

    /**
     * Delete both the signed document and the source document for a given filename.
     * Logs warnings on failure but does not throw, since the download has already succeeded.
     *
     * @param file the original filename (e.g. "91d56055f7274fdeb327077d1e32e5d1.pdf")
     */
    public void cleanupDocuments(String file) {
        String hash = file.replace(".pdf", "");

        Path signedPath = Paths.get(properties.getSignedDocumentsDir()).resolve(hash + "-signed.pdf");
        Path sourcePath = Paths.get(properties.getSourceDocumentsDir()).resolve(file);

        try {
            Files.deleteIfExists(signedPath);
            debug("Deleted signed document: {}", signedPath);
        } catch (IOException e) {
            LOG.warn("Failed to delete signed document: {}", signedPath, e);
        }

        try {
            Files.deleteIfExists(sourcePath);
            debug("Deleted source document: {}", sourcePath);
        } catch (IOException e) {
            LOG.warn("Failed to delete source document: {}", sourcePath, e);
        }
    }

    public void debug(String message, Object... args) {
        if (properties.isDebug()) {
            LOG.info(message, args);
        } else {
            LOG.debug(message, args);
        }
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    private static byte[] sha1(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return md.digest(input);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

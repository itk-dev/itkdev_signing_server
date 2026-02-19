package dk.itkdev.signing.controller;

import dk.gov.nemlogin.signing.dto.SigningPayloadDTO;
import dk.itkdev.signing.config.ItkdevProperties;
import dk.itkdev.signing.service.SigningService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

@Controller
public class SigningController {

    private static final String SESSION_FORWARD_URL = "forward_url";
    private static final String SESSION_FILENAME = "filename";

    private final SigningService signingService;
    private final ItkdevProperties properties;

    public SigningController(SigningService signingService, ItkdevProperties properties) {
        this.signingService = signingService;
        this.properties = properties;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("testPageEnabled", properties.isTestPageEnabled());
        return "index";
    }

    @GetMapping("/sign")
    public Object handleAction(
            @RequestParam String action,
            @RequestParam(required = false) String uri,
            @RequestParam(name = "forward_url", required = false) String forwardUrl,
            @RequestParam(required = false) String hash,
            @RequestParam(required = false) String file,
            HttpSession session,
            Model model) {

        try {
            return switch (action) {
                case "getcid" -> handleGetCid();
                case "sign" -> handleSign(uri, forwardUrl, hash, session, model);
                case "result" -> handleResult(file, "result", session);
                case "cancel" -> handleResult(file, "cancel", session);
                case "download" -> handleDownload(file);
                default -> errorResponse("Unexpected action: " + action);
            };
        } catch (Exception e) {
            signingService.debug("ERROR: {} {}", e.getMessage(), e);
            return errorResponse(e.getMessage());
        }
    }

    private ResponseEntity<?> handleGetCid() {
        String cid = signingService.generateCorrelationId();
        return ResponseEntity.ok(Map.of("cid", cid));
    }

    private String handleSign(String uri, String forwardUrl, String hash,
                              HttpSession session, Model model) throws Exception {
        if (uri == null || uri.isEmpty()) {
            throw new IllegalArgumentException("Parameter uri is required.");
        }
        if (forwardUrl == null || forwardUrl.isEmpty()) {
            throw new IllegalArgumentException("Parameter forward_url is required.");
        }
        if (hash == null || hash.isEmpty()) {
            throw new IllegalArgumentException("Parameter hash is required.");
        }

        // Decode forward_url from base64 and validate hash
        String decodedForwardUrl = new String(Base64.getDecoder().decode(forwardUrl));
        if (!signingService.validateHash(hash, decodedForwardUrl)) {
            throw new SecurityException("Incorrect hash value");
        }

        // Validate forward URL domain
        if (!signingService.isValidDomain(decodedForwardUrl)) {
            throw new SecurityException("Forward URL domain not allowed: " + decodedForwardUrl);
        }

        // Fetch and prepare the document
        String filename = signingService.fetchAndPrepareDocument(uri);

        // Store forward_url and filename in session
        session.setAttribute(SESSION_FORWARD_URL, forwardUrl);
        session.setAttribute(SESSION_FILENAME, filename);

        // Generate signing payload
        SigningPayloadDTO signingPayload = signingService.generateSigningPayload(filename);

        // Build model for the signing page
        model.addAttribute("signingPayload", signingPayload);
        model.addAttribute("signingClientUrl", signingService.getSigningClientUrl());
        model.addAttribute("document", Map.of("name", filename));
        model.addAttribute("format", "PAdES");
        model.addAttribute("correlationId", signingService.generateCorrelationId());

        return "sign";
    }

    private ResponseEntity<?> handleResult(String file, String resultAction, HttpSession session) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Parameter file is required.");
        }

        String forwardUrlB64 = (String) session.getAttribute(SESSION_FORWARD_URL);
        if (forwardUrlB64 == null || forwardUrlB64.isEmpty()) {
            throw new SecurityException("Session expired or forward_url not found.");
        }

        String forwardUrl = new String(Base64.getDecoder().decode(forwardUrlB64));
        if (!signingService.isValidDomain(forwardUrl)) {
            throw new SecurityException("Forward URL domain not allowed: " + forwardUrl);
        }

        // Clear session
        session.removeAttribute(SESSION_FORWARD_URL);
        session.removeAttribute(SESSION_FILENAME);

        String separator = forwardUrl.contains("?") ? "&" : "?";
        String redirectUrl = forwardUrl + separator + "file=" + file + "&action=" + resultAction;

        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
    }

    private ResponseEntity<byte[]> handleDownload(String file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Parameter file is required.");
        }

        Path signedPath = signingService.getSignedDocumentPath(file);

        if (!Files.exists(signedPath) || Files.size(signedPath) == 0) {
            signingService.debug("Attempt to download empty or nonexisting file {}", file);
            throw new SecurityException("Attempt to download empty or nonexisting file");
        }

        signingService.debug("Sending {} ({} bytes)", signedPath, Files.size(signedPath));

        byte[] pdfData = Files.readAllBytes(signedPath);
        signingService.cleanupDocuments(file);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfData.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
                .body(pdfData);
    }

    private ResponseEntity<?> errorResponse(String message) {
        return ResponseEntity.ok(Map.of(
                "error", true,
                "message", message != null ? message : "Unknown error",
                "code", 0
        ));
    }
}

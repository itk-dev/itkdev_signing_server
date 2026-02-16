package dk.itkdev.signing.controller;

import dk.gov.nemlogin.signing.dto.SigningPayloadDTO;
import dk.gov.nemlogin.signing.validation.model.ValidationReport;
import dk.itkdev.signing.config.ItkdevProperties;
import dk.itkdev.signing.service.SigningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.Map;

@Controller
public class TestController {

    private static final Logger LOG = LoggerFactory.getLogger(TestController.class);

    private static final String SESSION_FORWARD_URL = "forward_url";
    private static final String SESSION_FILENAME = "filename";

    private final ItkdevProperties properties;
    private final SigningService signingService;

    public TestController(ItkdevProperties properties, SigningService signingService) {
        this.properties = properties;
        this.signingService = signingService;
    }

    @GetMapping("/test")
    public String testPage() {
        if (!properties.isTestPageEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "test";
    }

    @PostMapping("/test/upload")
    public String testUpload(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request,
            HttpSession session,
            Model model) throws Exception {

        if (!properties.isTestPageEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        if (file.isEmpty() || !"application/pdf".equals(file.getContentType())) {
            throw new IllegalArgumentException("Please upload a valid PDF file.");
        }

        String filename = signingService.saveUploadedDocument(file.getBytes());

        // Build forward_url pointing to our own /test/result endpoint
        String baseUrl = buildBaseUrl(request);
        String forwardUrl = baseUrl + "/test/result";
        String forwardUrlB64 = Base64.getEncoder().encodeToString(forwardUrl.getBytes());

        // Store in session (same keys as production flow)
        session.setAttribute(SESSION_FORWARD_URL, forwardUrlB64);
        session.setAttribute(SESSION_FILENAME, filename);

        // Generate signing payload
        SigningPayloadDTO signingPayload = signingService.generateSigningPayload(filename);

        model.addAttribute("signingPayload", signingPayload);
        model.addAttribute("signingClientUrl", signingService.getSigningClientUrl());
        model.addAttribute("document", Map.of("name", filename));
        model.addAttribute("format", "PAdES");
        model.addAttribute("correlationId", signingService.generateCorrelationId());

        return "sign";
    }

    @GetMapping("/test/result")
    public String testResult(
            @RequestParam(required = false) String file,
            @RequestParam(required = false) String action,
            Model model) {

        if (!properties.isTestPageEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        model.addAttribute("file", file);
        model.addAttribute("action", action);

        return "test-result";
    }

    @PostMapping("/test/validate")
    public String testValidate(
            @RequestParam String file,
            Model model) {

        if (!properties.isTestPageEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        model.addAttribute("file", file);
        model.addAttribute("action", "result");

        try {
            ValidationReport validationReport = signingService.validateSignedDocument(file);
            model.addAttribute("validationReport", validationReport);
        } catch (Exception e) {
            LOG.error("Error validating signed document: {}", e.getMessage(), e);
            model.addAttribute("validationError", e.getMessage());
        }

        return "test-result";
    }

    private String buildBaseUrl(HttpServletRequest request) {
        String proto = request.getHeader("X-Forwarded-Proto");
        if (proto == null || proto.isEmpty()) {
            proto = request.getScheme();
        }

        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isEmpty()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isEmpty()) {
            host = request.getServerName() + ":" + request.getServerPort();
        }

        return proto + "://" + host;
    }
}

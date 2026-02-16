package dk.os2forms.signing.controller;

import dk.os2forms.signing.service.SigningService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Base64;
import java.util.Map;

/**
 * Handles the form POST from the signing iframe after the user signs, cancels, or encounters an error.
 */
@Controller
public class SigningResultController {

    private static final Logger LOG = LoggerFactory.getLogger(SigningResultController.class);

    private static final String SESSION_FORWARD_URL = "forward_url";
    private static final String SESSION_FILENAME = "filename";

    private final SigningService signingService;

    public SigningResultController(SigningService signingService) {
        this.signingService = signingService;
    }

    @PostMapping("/signing-result")
    public ResponseEntity<?> handleSigningResult(
            @RequestParam String type,
            @RequestParam(required = false) String result,
            @RequestParam String name,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String correlationId,
            HttpSession session) {

        try {
            String filename = (String) session.getAttribute(SESSION_FILENAME);
            if (filename == null) {
                filename = name;
            }

            return switch (type) {
                case "signedDocument" -> handleSignedDocument(result, filename, session);
                case "cancelSign" -> handleCancelSign(filename, session);
                case "errorResponse" -> handleErrorResponse(result, filename, session);
                default -> {
                    LOG.warn("Unknown signing result type: {}", type);
                    yield handleErrorResponse("Unknown result type: " + type, filename, session);
                }
            };
        } catch (Exception e) {
            LOG.error("Error handling signing result", e);
            return errorResponse(e.getMessage());
        }
    }

    private ResponseEntity<?> handleSignedDocument(String base64Result, String filename, HttpSession session) throws Exception {
        // Save the signed document
        signingService.saveSignedDocument(filename, base64Result);

        // Read forward_url from session
        String forwardUrlB64 = (String) session.getAttribute(SESSION_FORWARD_URL);
        if (forwardUrlB64 == null || forwardUrlB64.isEmpty()) {
            throw new SecurityException("Session expired or forward_url not found.");
        }

        String forwardUrl = new String(Base64.getDecoder().decode(forwardUrlB64));
        if (!signingService.isValidDomain(forwardUrl)) {
            throw new SecurityException("Forward URL domain not allowed: " + forwardUrl);
        }

        // Clear session
        clearSession(session);

        // Redirect to OS2Forms with result
        String separator = forwardUrl.contains("?") ? "&" : "?";
        String redirectUrl = forwardUrl + separator + "file=" + filename + "&action=result";

        signingService.debug("Signed document saved, redirecting to: {}", redirectUrl);

        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
    }

    private ResponseEntity<?> handleCancelSign(String filename, HttpSession session) {
        String forwardUrlB64 = (String) session.getAttribute(SESSION_FORWARD_URL);
        if (forwardUrlB64 == null || forwardUrlB64.isEmpty()) {
            throw new SecurityException("Session expired or forward_url not found.");
        }

        String forwardUrl = new String(Base64.getDecoder().decode(forwardUrlB64));
        if (!signingService.isValidDomain(forwardUrl)) {
            throw new SecurityException("Forward URL domain not allowed: " + forwardUrl);
        }

        // Clear session
        clearSession(session);

        String separator = forwardUrl.contains("?") ? "&" : "?";
        String redirectUrl = forwardUrl + separator + "file=" + filename + "&action=cancel";

        signingService.debug("Signing cancelled, redirecting to: {}", redirectUrl);

        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
    }

    private ResponseEntity<?> handleErrorResponse(String errorMessage, String filename, HttpSession session) {
        if (errorMessage != null) {
            try {
                String decoded = new String(Base64.getDecoder().decode(errorMessage));
                LOG.error("Signing error (decoded): {}", decoded);
            } catch (Exception e) {
                LOG.error("Signing error: {}", errorMessage);
            }
        }

        String forwardUrlB64 = (String) session.getAttribute(SESSION_FORWARD_URL);
        if (forwardUrlB64 == null || forwardUrlB64.isEmpty()) {
            clearSession(session);
            return errorResponse("Session expired or forward_url not found after signing error.");
        }

        String forwardUrl = new String(Base64.getDecoder().decode(forwardUrlB64));

        // Clear session
        clearSession(session);

        if (!signingService.isValidDomain(forwardUrl)) {
            return errorResponse("Forward URL domain not allowed after signing error.");
        }

        String separator = forwardUrl.contains("?") ? "&" : "?";
        String redirectUrl = forwardUrl + separator + "file=" + filename + "&action=error";

        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, redirectUrl)
                .build();
    }

    private void clearSession(HttpSession session) {
        session.removeAttribute(SESSION_FORWARD_URL);
        session.removeAttribute(SESSION_FILENAME);
    }

    private ResponseEntity<?> errorResponse(String message) {
        return ResponseEntity.ok(Map.of(
                "error", true,
                "message", message != null ? message : "Unknown error",
                "code", 0
        ));
    }
}

package com.bepmo.auth.exception;

import com.bepmo.common.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Raised after refresh-token reuse has been detected and the user's active
 * refresh-token family has been revoked. AuthService.refresh deliberately does
 * not roll back for this exception so the security invalidation is committed.
 */
public class RefreshTokenReuseException extends AppException {

    public RefreshTokenReuseException() {
        super(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected. Please login again.");
    }
}

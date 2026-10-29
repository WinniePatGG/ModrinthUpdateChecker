package de.winniepat.modrinthupdatechecker.exceptions;

/**
 * Thrown when Modrinth answers with HTTP 429, i.e. your client hit the API
 * rate limit. Callers should back off before retrying; consider scheduling
 * update checks on a fixed interval instead of every tick or login.
 */
public class RateLimitException extends ModrinthApiException {

    private static final long serialVersionUID = 1L;

    /** Seconds suggested by the Retry-After header, or -1. */
    private final long retryAfterSeconds;

    /**
     * Creates an exception reporting the rate limit hit.
     *
     * @param message what went wrong
     * @param retryAfterSeconds the Retry-After suggestion in seconds, or
     *                          {@code null} when the header was absent or
     *                          unparseable
     */
    public RateLimitException(String message, Long retryAfterSeconds) {
        super(429, message);
        this.retryAfterSeconds = retryAfterSeconds == null ? -1 : retryAfterSeconds;
    }

    /**
     * Seconds the server suggested to wait before retrying.
     *
     * @return the Retry-After value in seconds, or {@code -1} if the server
     *         did not provide it
     */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}

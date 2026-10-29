package de.winniepat.modrinthupdatechecker.exceptions;

/**
 * Thrown when the Modrinth API answered with an unexpected HTTP status.
 *
 * <p>Some statuses have dedicated subclasses; check those first:
 * {@link ProjectNotFoundException} for HTTP 404 and {@link RateLimitException}
 * for HTTP 429.
 */
public class ModrinthApiException extends ModrinthException {

    private static final long serialVersionUID = 1L;

    /** The HTTP status code the Modrinth API answered with. */
    private final int statusCode;

    /**
     * Creates an exception for an unexpected HTTP status.
     *
     * @param statusCode the HTTP status code
     * @param message what went wrong
     */
    public ModrinthApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Creates an exception for an unexpected HTTP status with a cause.
     *
     * @param statusCode the HTTP status code
     * @param message what went wrong
     * @param cause the underlying failure
     */
    public ModrinthApiException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * The HTTP status code the Modrinth API answered with.
     *
     * @return e.g. {@code 500}, {@code 503}; never a status with a dedicated
     *         subclass
     */
    public int statusCode() {
        return statusCode;
    }
}

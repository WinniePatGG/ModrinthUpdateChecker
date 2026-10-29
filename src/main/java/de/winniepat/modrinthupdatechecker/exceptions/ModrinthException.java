package de.winniepat.modrinthupdatechecker.exceptions;

/**
 * Base class of all exceptions thrown by this library. It is unchecked so
 * that it can flow through {@link java.util.concurrent.CompletableFuture}
 * chains without checked-exception ceremony.
 *
 * <p>Concrete subclasses carry the specific failure:
 * {@link ProjectNotFoundException}, {@link RateLimitException},
 * {@link ModrinthApiException}, {@link NoCompatibleVersionException}, and
 * network problems surface as this base class with an {@link java.io.IOException}
 * cause.
 */
public class ModrinthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message and no cause.
     *
     * @param message what went wrong
     */
    public ModrinthException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and an underlying cause.
     *
     * @param message what went wrong
     * @param cause the underlying failure, e.g. an {@link java.io.IOException}
     */
    public ModrinthException(String message, Throwable cause) {
        super(message, cause);
    }
}

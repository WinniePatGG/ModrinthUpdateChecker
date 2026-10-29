package de.winniepat.modrinthupdatechecker.exceptions;

import de.winniepat.modrinthupdatechecker.ModrinthUpdateChecker;

/**
 * Thrown when the Modrinth project exists but no listed version matches the
 * configured {@link ModrinthUpdateChecker.Builder#loader(String)} and/or
 * {@link ModrinthUpdateChecker.Builder#gameVersion(String)} filters.
 */
public class NoCompatibleVersionException extends ModrinthException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception reporting the empty result.
     *
     * @param message what went wrong, including the project and the active
     *                filters
     */
    public NoCompatibleVersionException(String message) {
        super(message);
    }
}

package de.winniepat.modrinthupdatechecker.exceptions;

/**
 * Thrown when Modrinth answers with HTTP 404, i.e. the given project ID or
 * slug does not exist.
 */
public class ProjectNotFoundException extends ModrinthApiException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception reporting the missing project.
     *
     * @param message what went wrong, including the project ID or slug
     */
    public ProjectNotFoundException(String message) {
        super(404, message);
    }
}

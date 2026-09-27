package io.blockdesigner.plugin;

import java.util.Objects;

/**
 * How {@link PluginContext#openFile} went: opened, cancelled by the user (at the save-changes question), or failed,
 * with a short reason for the user ("Could not read Castle.litematic: …"). Since API 7.
 */
public record OpenResult(Status status, String message) {

    public enum Status { OPENED, CANCELLED, FAILED }

    public OpenResult {
        Objects.requireNonNull(status, "status");
        message = message == null ? "" : message;
    }

    public static OpenResult opened() {
        return new OpenResult(Status.OPENED, "");
    }

    public static OpenResult cancelled() {
        return new OpenResult(Status.CANCELLED, "Cancelled");
    }

    public static OpenResult failed(String message) {
        return new OpenResult(Status.FAILED, message);
    }

    /** True when the file is now the open project. */
    public boolean isOpened() {
        return status == Status.OPENED;
    }
}

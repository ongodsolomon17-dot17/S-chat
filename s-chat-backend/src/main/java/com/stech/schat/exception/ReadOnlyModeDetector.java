package com.stech.schat.exception;

import java.sql.SQLException;

/** Recognises "tried to write while connected to the read-only fallback database" (PostgreSQL SQLState 25006). */
public final class ReadOnlyModeDetector {

    public static final String USER_MESSAGE =
            "S-Chat is running on its backup database right now, so changes are paused. You can still read your messages. Please try again shortly.";

    private ReadOnlyModeDetector() {}

    public static boolean isReadOnlyViolation(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException sql) {
                for (SQLException s = sql; s != null; s = s.getNextException()) {
                    if ("25006".equals(s.getSQLState())) return true;
                }
            }
        }
        return false;
    }
}

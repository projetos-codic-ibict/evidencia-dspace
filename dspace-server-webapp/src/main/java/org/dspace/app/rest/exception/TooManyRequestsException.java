/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * When a client sends requests faster than the endpoint allows, we use this exception to
 * indicate this to the client.
 */
@ResponseStatus(value = HttpStatus.TOO_MANY_REQUESTS, reason = "Too Many Requests")
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}

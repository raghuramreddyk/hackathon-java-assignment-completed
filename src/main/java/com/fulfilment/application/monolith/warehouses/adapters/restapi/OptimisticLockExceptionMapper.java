package com.fulfilment.application.monolith.warehouses.adapters.restapi;

import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Globally catches JPA OptimisticLockExceptions and translates them into
 * standard HTTP 409 Conflict responses, ensuring the client knows their
 * state is stale without crashing the server with an HTTP 500.
 */
@Provider
public class OptimisticLockExceptionMapper implements ExceptionMapper<OptimisticLockException> {

  @Override
  public Response toResponse(OptimisticLockException exception) {
    return Response.status(Response.Status.CONFLICT)
        .entity(new ErrorResponse(
            "Conflict: The resource was modified by another transaction. Please refresh and try again."))
        .build();
  }

  public static class ErrorResponse {
    public String error;

    public ErrorResponse(String error) {
      this.error = error;
    }
  }
}

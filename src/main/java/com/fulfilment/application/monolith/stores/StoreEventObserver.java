package com.fulfilment.application.monolith.stores;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class StoreEventObserver {

  private static final Logger LOGGER = Logger.getLogger(StoreEventObserver.class.getName());

  @Inject
  LegacyStoreManagerGateway legacyStoreManagerGateway;

  /**
   * Observes StoreCreatedEvent AFTER_SUCCESS — CDI only delivers this event
   * after the originating transaction has committed successfully.
   * If the transaction rolls back (e.g. duplicate name constraint), this
   * method is never called, preventing spurious legacy-system notifications.
   */
  public void onStoreCreated(@Observes(during = TransactionPhase.AFTER_SUCCESS) StoreCreatedEvent event) {
    LOGGER.info("Store created event received, syncing with legacy system: " + event.getStore().id);
    legacyStoreManagerGateway.createStoreOnLegacySystem(event.getStore());
  }

  public void onStoreUpdated(@Observes(during = TransactionPhase.AFTER_SUCCESS) StoreUpdatedEvent event) {
    LOGGER.info("Store updated event received, syncing with legacy system: " + event.getStore().id);
    legacyStoreManagerGateway.updateStoreOnLegacySystem(event.getStore());
  }
}

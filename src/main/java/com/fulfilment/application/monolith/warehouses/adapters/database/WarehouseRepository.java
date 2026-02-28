package com.fulfilment.application.monolith.warehouses.adapters.database;

import com.fulfilment.application.monolith.warehouses.domain.models.Warehouse;
import com.fulfilment.application.monolith.warehouses.domain.ports.WarehouseStore;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class WarehouseRepository implements WarehouseStore, PanacheRepository<DbWarehouse> {

  @Override
  public List<Warehouse> getAll() {
    // Only return active (non-archived) warehouses
    return list("archivedAt IS NULL").stream().map(DbWarehouse::toWarehouse).toList();
  }

  @Override
  public void create(Warehouse warehouse) {
    DbWarehouse dbWarehouse = new DbWarehouse();
    dbWarehouse.businessUnitCode = warehouse.businessUnitCode;
    dbWarehouse.location = warehouse.location;
    dbWarehouse.capacity = warehouse.capacity;
    dbWarehouse.stock = warehouse.stock;
    dbWarehouse.createdAt = warehouse.createdAt;
    dbWarehouse.archivedAt = warehouse.archivedAt;

    this.persist(dbWarehouse);
  }

  @Override
  public void update(Warehouse warehouse) {
    // Use JPA-managed entity so that @Version is enforced.
    // A bulk JPQL UPDATE bypasses the version check entirely and would
    // silently allow lost updates under concurrent access.
    DbWarehouse managed = find("businessUnitCode", warehouse.businessUnitCode).firstResult();
    if (managed == null) {
      throw new IllegalArgumentException(
          "Warehouse with business unit code '" + warehouse.businessUnitCode + "' not found for update");
    }

    // Apply changes onto the managed entity.
    // JPA will increment the @Version column and throw OptimisticLockException
    // on flush if another transaction committed a change in the meantime.
    managed.location = warehouse.location;
    managed.capacity = warehouse.capacity;
    managed.stock = warehouse.stock;
    managed.archivedAt = warehouse.archivedAt;

    // persist() is a no-op for already-managed entities; flush forces the
    // version check and the UPDATE to happen within this transaction.
    getEntityManager().flush();
  }

  @Override
  public void remove(Warehouse warehouse) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'remove'");
  }

  @Override
  public Warehouse findByBusinessUnitCode(String buCode) {
    DbWarehouse dbWarehouse = find("businessUnitCode", buCode).firstResult();
    return dbWarehouse != null ? dbWarehouse.toWarehouse() : null;
  }
}

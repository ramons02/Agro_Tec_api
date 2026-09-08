package com.agroclima.api.core.base;

import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;

/** Campos/comportamento comuns das entidades JPA -- equivalente ao papel do BaseModel do Pydantic no Python. */
@MappedSuperclass
public abstract class BaseModel<ID extends Serializable> {

    public abstract ID getId();

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof BaseModel<?> other) || getClass() != outro.getClass()) {
            return false;
        }
        ID id = getId();
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

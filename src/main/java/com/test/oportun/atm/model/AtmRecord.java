package com.test.oportun.atm.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The mongodb ATM record collection that holds the denomination and the 
 * corresponding quantities.
 */
@Document(collection = "atm")
public class AtmRecord {
    @Id
    private Integer denomination;

    private Integer quantity;

    public Integer getDenomination() {
        return this.denomination;
    }

    public void setDenomination(Integer denomination) {
        this.denomination = denomination;
    }

    public Integer getQuantity() {
        return this.quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

}
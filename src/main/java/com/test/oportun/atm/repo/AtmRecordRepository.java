package com.test.oportun.atm.repo;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.test.oportun.atm.model.AtmRecord;

public interface AtmRecordRepository extends MongoRepository<AtmRecord, Integer>{
    
}
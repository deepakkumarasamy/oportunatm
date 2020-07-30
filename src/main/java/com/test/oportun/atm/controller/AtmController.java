package com.test.oportun.atm.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.test.oportun.atm.repo.AtmRecordRepository;
import com.test.oportun.atm.model.AtmRecord;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@EnableMongoRepositories(basePackageClasses = AtmRecordRepository.class)
@RestController
@RequestMapping("/atm")
public class AtmController {

    @Autowired
	private AtmRecordRepository atmRecordRepository;

    /**
     * The method that caters to the ATM deposit requests
     * @param hmInAtmRecords input data
     * @return The balance after the deposit, both total and denomination-wise
     */
    @PostMapping("/deposit")
	public HashMap<String, Object> deposit(@RequestBody final Map<Integer, Integer> hmInAtmRecords) {

        return processDepositValues(hmInAtmRecords);

    }

    /**
     * A method containing the actual deposit processing logic
     * @param hmInAtmRecords input data
     * @return The balance after the deposit, both total and denomination-wise
     */
    private HashMap<String, Object> processDepositValues(final Map<Integer, Integer> hmInAtmRecords) {

        final HashMap<String, Object> hmResponse = new HashMap<String, Object>();

        String strError = validateDepositInput(hmInAtmRecords);

        //Do not process further in case there is an error in the input data
        if (strError != null && strError.length() > 0) {
            hmResponse.put("error", strError);
        } else {

            //Fetch the current data from the DB
            final ArrayList<AtmRecord> arrDBAtmRecords = (ArrayList<AtmRecord>) atmRecordRepository
                    .findAll(Sort.by(Sort.Direction.DESC, "_id"));
            LinkedHashMap<Integer, AtmRecord> lhMapDBAtmRecords;
            
            ArrayList<AtmRecord> arrUpdDBAtmRecord = arrDBAtmRecords.stream()
            			.filter(dbAtmRecord -> hmInAtmRecords.get(dbAtmRecord.getDenomination()) != null)
            			.map(dbAtmRecord -> 
            						{ 
	            						dbAtmRecord.setQuantity(dbAtmRecord.getQuantity() 
	            												+ hmInAtmRecords.get(dbAtmRecord.getDenomination()));
	            						return dbAtmRecord;
            						})
            			.collect(Collectors.toCollection(ArrayList::new));

            //Update the database with the incremented values
            lhMapDBAtmRecords = convertToLhMap((ArrayList<AtmRecord>) atmRecordRepository.saveAll(arrUpdDBAtmRecord));

            //Fetch the total balance
            int totalBalance = calculateTotalBalance(lhMapDBAtmRecords);
            hmResponse.put("balanceDenom", lhMapDBAtmRecords);
            hmResponse.put("totalBalance", totalBalance);
        }

        return hmResponse;
    }

    /**
     * A helper method to calculate the total balance
     * @param lhMapDBAtmRecords Hashmap containing the denomination-wise quantities
     * @return Total available balance 
     */
    private int calculateTotalBalance(Map<Integer, AtmRecord> lhMapDBAtmRecords) {

    	return lhMapDBAtmRecords
    				.values().stream()
					.map(atmRecord -> {return atmRecord.getDenomination() * atmRecord.getQuantity();})
					.collect(Collectors.summingInt(Integer::intValue));

    }

    /**
     * A validator method to check the validity of the input deposit data
     * @param hmInAtmRecords Input deposit data
     * @return Contains the error description, in case there is one
     */
    private String validateDepositInput(Map<Integer, Integer> hmInAtmRecords) {

    	String strError = null;

        if(hmInAtmRecords.values().stream().noneMatch((quantity) -> quantity > 0)) {
        	strError = "Deposit amount cannot be zero";
        } else if (hmInAtmRecords.values().stream().anyMatch((quantity) -> quantity < 0)) {
                strError = "Incorrect deposit amount";
        }
        
        return strError;
    }

    /**
     * The method that caters to the ATM withdrawal requests
     * @param cashRequest Contains the request amount
     * @return The total available balance after withdrawal, denomination-wise 
     *          dispersal data and available balance.
     */
    @PostMapping("/withdraw")
    public HashMap<String, Object> withdraw(@RequestBody final Map<String, Integer> cashRequest) {

        return processDispensal((Integer) cashRequest.get("intCashRequest"));
    }

    /**
     * The actual method that contains the withdrawal logic
     * @param cashRequest Contains the request amount
     * @return The total available balance after withdrawal, denomination-wise 
     *          dispersal data and available balance.
     */
    private HashMap<String, Object> processDispensal(final Integer intCashRequest) {

        //Fetch the current data from the DB in the descending order of denominations
        ArrayList<AtmRecord> arrDBAtmRecords = (ArrayList<AtmRecord>) atmRecordRepository
                .findAll(Sort.by(Sort.Direction.DESC, "_id"));
        LinkedHashMap<Integer, AtmRecord> lhMapDBAtmRecords = convertToLhMap(arrDBAtmRecords);
        final HashMap<String, Object> hmResponse = new HashMap<String, Object>();

        //Check the validity of the input request data
        String strError = validateWithdrawal(intCashRequest, lhMapDBAtmRecords);

        if (strError != null && strError.length() > 0) {
            hmResponse.put("error", strError);
        } else {
            final LinkedHashMap<Integer, AtmRecord> resCashDispensal = new LinkedHashMap<Integer, AtmRecord>();

            //Assign request amount to tmpBalance
            int tmpBalance = intCashRequest;
            int tmpDenomCount = 0;
            int tmpDenomBalance = 0;
            AtmRecord tmpAtmRecord = null;
            AtmRecord tmpResAtmRecord = null;

            final Iterator<Integer> iterator = lhMapDBAtmRecords.keySet().iterator();

            //Loop until tmpBalance is greater than zero and all the denominations are processed
            while (iterator.hasNext() && tmpBalance > 0) {
                final Integer i = iterator.next();
                tmpAtmRecord = lhMapDBAtmRecords.get(i);

                // Find the number of notes that can be dispensed in each denomination
                tmpDenomCount = new Double(Math.floor(tmpBalance / tmpAtmRecord.getDenomination())).intValue();
                if (tmpDenomCount > 0) {
                    tmpResAtmRecord = new AtmRecord();

                    // Calculate the remaining quantity of notes in each denomination
                    tmpDenomBalance = tmpAtmRecord.getQuantity() - tmpDenomCount;

                    // If the number of notes is more than the available quantity 
                    // then dispense the available quantity
                    if (tmpDenomBalance < 0) {
                        tmpDenomBalance = 0;
                        tmpDenomCount = tmpAtmRecord.getQuantity();
                    }

                    // Reduce the dispensed amount from the total request amount
                    tmpBalance = tmpBalance - (tmpDenomCount * tmpAtmRecord.getDenomination());

                    //Set the updated quantity to be updated in the DB
                    tmpAtmRecord.setQuantity(tmpDenomBalance);

                    //Set the values to be shown as dispensed 
                    tmpResAtmRecord.setDenomination(tmpAtmRecord.getDenomination());
                    tmpResAtmRecord.setQuantity(tmpDenomCount);

                    resCashDispensal.put(tmpAtmRecord.getDenomination(), tmpResAtmRecord);

                }
            }

            //tmpBalance equals zero signifies that the input amount was dispensed using the available denominations
            if (tmpBalance == 0) {
                lhMapDBAtmRecords = convertToLhMap((ArrayList<AtmRecord>) atmRecordRepository.saveAll(arrDBAtmRecords));

                int totalBalance = calculateTotalBalance(lhMapDBAtmRecords);
                hmResponse.put("dispense", resCashDispensal);
                hmResponse.put("balanceDenom", lhMapDBAtmRecords);
                hmResponse.put("totalBalance", totalBalance);

            } else {
                //If tmpBalance has a value other than zero then it means that the input amount was not able to 
                // be dispensed using the available denominations even though the balance was sufficient enough.
                hmResponse.put("error",
                        "Unable to dispense. Change the request amount as per the available denominations.");
                arrDBAtmRecords = (ArrayList<AtmRecord>) atmRecordRepository.findAll(Sort.by(Sort.Direction.DESC, "_id"));
                hmResponse.put("balance", convertToLhMap(arrDBAtmRecords));
            }
        }
        return hmResponse;
    }

    /**
     * A validator method to validate the withdrawl requests
     * @param intCashRequest Input request amount
     * @param lhMapDBAtmRecords Contains the appropriate error message, if there is any
     * @return
     */
    private String validateWithdrawal(Integer intCashRequest, Map<Integer, AtmRecord> lhMapDBAtmRecords) {
        
        String strError = null;
        int totalBalance = calculateTotalBalance(lhMapDBAtmRecords);
        if(intCashRequest <= 0 || intCashRequest > totalBalance) {
            strError = "Incorrect or insufficient funds";
        }

        return strError;
    }

    /**
     * The method that helps to add a new denomination
     * @param atmRecord The new denomination record
     * @return The created denomination record.
     */
    @PostMapping("/addDenomination")
    public AtmRecord addDenomination(@RequestBody final AtmRecord atmRecord) {
        return atmRecordRepository.save(atmRecord);
    }

    /**
     * A utility method to convert the database objects into hashmaps for 
     * the ease of processing.
     * @param arrAtmRecords
     * @return
     */
    private LinkedHashMap<Integer, AtmRecord> convertToLhMap(final ArrayList<AtmRecord> arrAtmRecords) {

    	return arrAtmRecords.stream().sorted(Comparator.comparing(AtmRecord::getDenomination).reversed())
    						.collect(Collectors.toMap(AtmRecord::getDenomination, 
    													atmRecord -> atmRecord, 
    													(e1, e2) -> e1, 
    													LinkedHashMap::new));
	}
}
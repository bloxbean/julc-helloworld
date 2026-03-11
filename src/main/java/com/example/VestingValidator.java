package com.example;

import com.bloxbean.cardano.julc.core.PlutusData;
import com.bloxbean.cardano.julc.ledger.PubKeyHash;
import com.bloxbean.cardano.julc.ledger.ScriptContext;
import com.bloxbean.cardano.julc.ledger.TxInfo;
import com.bloxbean.cardano.julc.stdlib.annotation.Entrypoint;
import com.bloxbean.cardano.julc.stdlib.annotation.SpendingValidator;
import com.bloxbean.cardano.julc.stdlib.lib.ContextsLib;

import java.math.BigInteger;

@SpendingValidator
public class VestingValidator {

    public record VestingDatum(PubKeyHash beneficiary, BigInteger deadline) {}

    @Entrypoint
    static boolean validate(VestingDatum datum, PlutusData redeemer, ScriptContext ctx) {
        TxInfo txInfo = ctx.txInfo();

        ContextsLib.trace("Checking beneficiary");
        // Check that the beneficiary signed the transaction
        boolean signed = txInfo.signatories().contains(datum.beneficiary());

        // Check that the deadline has passed (lower bound of valid range > deadline)
        // Just a dummy check to demonstrate using the datum's deadline field.
        boolean pastDeadline = datum.deadline().compareTo(BigInteger.ZERO) > 0;

        return signed && pastDeadline;
    }
}

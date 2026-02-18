package com.example;

import com.bloxbean.cardano.julc.core.PlutusData;
import com.bloxbean.cardano.julc.core.types.JulcList;
import com.bloxbean.cardano.julc.core.types.JulcMap;
import com.bloxbean.cardano.julc.ledger.*;
import com.bloxbean.cardano.julc.testkit.BudgetAssertions;
import com.bloxbean.cardano.julc.testkit.ContractTest;
import com.bloxbean.cardano.julc.testkit.TestDataBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for VestingValidator — the most complex validator with @Param, nested records,
 * for-each loops, and multi-file compilation (uses SumTest library).
 */
class VestingValidatorTest extends ContractTest {

    static final byte[] BENEFICIARY = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 24, 25, 26, 27, 28};
    static final byte[] OTHER_PKH = new byte[]{99, 98, 97, 96, 95, 94, 93, 92, 91, 90,
            89, 88, 87, 86, 85, 84, 83, 82, 81, 80,
            79, 78, 77, 76, 75, 74, 73, 72};

    @BeforeAll
    static void setup() {
        initCrypto();
    }

    // ---- Mode A: Direct Java tests ----

    @Nested
    class DirectJavaTests {

        private ScriptContext buildCtx(byte[] signer, TxOut... outputs) {
            var txInfo = new TxInfo(
                    JulcList.of(), JulcList.of(),
                    JulcList.of(outputs),
                    BigInteger.valueOf(200_000),
                    Value.zero(),
                    JulcList.of(), JulcMap.empty(),
                    Interval.always(),
                    JulcList.of(new PubKeyHash(signer)),
                    JulcMap.empty(), JulcMap.empty(),
                    new TxId(new byte[32]),
                    JulcMap.empty(), JulcList.of(),
                    Optional.empty(), Optional.empty());
            return new ScriptContext(txInfo, PlutusData.UNIT,
                    new ScriptInfo.SpendingScript(new TxOutRef(new TxId(new byte[32]), BigInteger.ZERO), Optional.empty()));
        }

        private TxOut makeOutput(BigInteger lovelace) {
            return new TxOut(
                    new Address(new Credential.PubKeyCredential(new PubKeyHash(new byte[28])), Optional.empty()),
                    Value.lovelace(lovelace),
                    new OutputDatum.NoOutputDatum(),
                    Optional.empty());
        }

        @Test
        void beneficiaryCanUnlock() {
            var datum = new VestingValidator.VestingDatum(PubKeyHash.of(BENEFICIARY), BigInteger.valueOf(42));

            // 2 outputs, one with exactly 5M lovelace
            var output1 = makeOutput(BigInteger.valueOf(5_000_000));
            var output2 = makeOutput(BigInteger.valueOf(2_000_000));
            var ctx = buildCtx(BENEFICIARY, output1, output2);

            boolean result = VestingValidator.validate(datum, PlutusData.UNIT, ctx);
            assertTrue(result, "Beneficiary with correct params should pass");
        }

        @Test
        void wrongSigner_fails() {
            var datum = new VestingValidator.VestingDatum(PubKeyHash.of(BENEFICIARY), BigInteger.valueOf(42));

            var output1 = makeOutput(BigInteger.valueOf(5_000_000));
            var output2 = makeOutput(BigInteger.valueOf(2_000_000));
            var ctx = buildCtx(OTHER_PKH, output1, output2); // wrong signer

            boolean result = VestingValidator.validate(datum, PlutusData.UNIT, ctx);
            assertFalse(result, "Wrong signer should fail");
        }
    }
    // ---- Mode B: UPLC compilation tests ----

    @Nested
    class UplcTests {

        // VestingDatum: Constr(0, [BData(beneficiary)])
        private PlutusData buildDatum(byte[] beneficiary) {
            return PlutusData.constr(0, PlutusData.bytes(beneficiary), PlutusData.integer(42));
        }


        private PlutusData buildRedeemer(long no, String msg) {
            return PlutusData.UNIT;
        }

        // Build ScriptContext with datum in scriptInfo, redeemer in ctx
        private PlutusData buildCtxData(PlutusData datum, PlutusData redeemer,
                                        byte[] signer, BigInteger... outputLovelaces) {
            var ref = TestDataBuilder.randomTxOutRef_typed();
            var builder = spendingContext(ref, datum).redeemer(redeemer).signer(signer);
            for (BigInteger lovelace : outputLovelaces) {
                var address = TestDataBuilder.pubKeyAddress(new PubKeyHash(signer));
                var txOut = TestDataBuilder.txOut(address,
                        Value.lovelace(lovelace));
                builder.output(txOut);
            }
            return builder.buildPlutusData();
        }

        @Test
        void compiles_andEvaluates() throws Exception {
            var program = compileValidator(VestingValidator.class).program();

            var datum = buildDatum(BENEFICIARY);
            var redeemer = buildRedeemer(42, "hello");
            var ctx = buildCtxData(datum, redeemer, BENEFICIARY,
                    BigInteger.valueOf(5_000_000),
                    BigInteger.valueOf(2_000_000));

            var result = evaluate(program, ctx);
            assertSuccess(result);
            logBudget("compilesWithParams_andEvaluates", result);
        }


        @Test
        void tracesAreEmitted() throws Exception {
            var program = compileValidator(VestingValidator.class).program();

            var datum = buildDatum(BENEFICIARY);
            var redeemer = buildRedeemer(42, "hello");
            var ctx = buildCtxData(datum, redeemer, BENEFICIARY,
                    BigInteger.valueOf(5_000_000),
                    BigInteger.valueOf(2_000_000));

            var result = evaluate(program, ctx);
            assertSuccess(result);
            BudgetAssertions.assertTrace(result,  "Checking beneficiary");
        }
    }

    private static void logBudget(String testName, com.bloxbean.cardano.julc.vm.EvalResult result) {
        var budget = result.budgetConsumed();
        System.out.println("[" + testName + "] CPU: " + budget.cpuSteps() + ", Mem: " + budget.memoryUnits());
        System.out.println("[" + testName + "] Traces: " + result.traces());
    }
}

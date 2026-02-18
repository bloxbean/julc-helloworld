# JuLC Hello World - Vesting Validator Example

A starter template for writing Cardano smart contracts in Java using [JuLC](https://github.com/bloxbean/julc) (Java UPLC Compiler).
This project demonstrates a complete end-to-end workflow: writing a spending validator in Java, testing it locally,
and deploying it on a local devnet using [Yaci Devkit](https://github.com/bloxbean/yaci-devkit).

Use this as a template to build your own Cardano validators in Java.

## What's Inside

| File | Description |
|------|-------------|
| `VestingValidator.java` | A spending validator that locks ADA until the beneficiary signs and the deadline condition is met |
| `VestingOffchainApp.java` | Off-chain code that locks and unlocks ADA using cardano-client-lib |
| `YaciHelper.java` | Utility class for interacting with Yaci Devkit (topup, UTXO lookup, tx confirmation) |
| `VestingValidatorTest.java` | Unit tests — both direct Java tests and UPLC compilation + Scalus VM evaluation tests |

## Prerequisites

- **Java 24+**
- **Gradle 9+** (or use the included Gradle wrapper)
- **Yaci Devkit** (required only for the off-chain demo)

## The Validator

`VestingValidator.java` is a simple spending validator annotated with `@SpendingValidator`. It checks two conditions:

1. The beneficiary's public key hash is in the transaction signatories
2. The deadline value in the datum is greater than zero

```java
@SpendingValidator
public class VestingValidator {

    record VestingDatum(PubKeyHash beneficiary, BigInteger deadline) {}

    @Entrypoint
    static boolean validate(VestingDatum datum, PlutusData redeemer, ScriptContext ctx) {
        TxInfo txInfo = ctx.txInfo();
        boolean signed = txInfo.signatories().contains(datum.beneficiary());

        // Check that the deadline has passed (lower bound of valid range > deadline)
        // Just a dummy check to demonstrate using the datum's deadline field.
        boolean pastDeadline = datum.deadline().compareTo(BigInteger.ZERO) > 0;
        return signed && pastDeadline;
    }
}
```

## How Compilation Works

During `./gradlew build`, the JuLC annotation processor and compiler kick in as part of `javac`:

1. The annotation processor detects classes annotated with `@SpendingValidator` (or `@MintingValidator`)
2. The JuLC compiler compiles the Java validator code to Plutus V3 UPLC
3. The compiled output is written as a `<ValidatorName>.plutus.json` file to `build/classes/META-INF/plutus/`
4. This file is automatically included in the project's JAR

At runtime, `JulcScriptLoader.load(VestingValidator.class)` looks up the corresponding `.plutus.json` from `META-INF/plutus/` on the classpath, extracts the compiled CBOR, and returns a ready-to-use `PlutusScript` for off-chain transaction building.

## Build

```bash
./gradlew build
```

This compiles the validator, runs the annotation processor to generate the UPLC script, and executes the unit tests.

After building, you can find the compiled script at:
```
build/classes/java/main/META-INF/plutus/VestingValidator.plutus.json
```

## Running Tests

```bash
./gradlew test
```

The test class `VestingValidatorTest` demonstrates two testing approaches:

### 1. Direct Java Tests

Test the validator logic directly as plain Java — no compilation or VM needed. This is fast and useful for quick iteration.

```java
var datum = new VestingValidator.VestingDatum(PubKeyHash.of(BENEFICIARY), BigInteger.valueOf(42));
var ctx = buildCtx(BENEFICIARY, output1, output2);
boolean result = VestingValidator.validate(datum, PlutusData.UNIT, ctx);
assertTrue(result);
```

### 2. UPLC Compilation + Scalus VM Tests

Compile the validator to UPLC, then evaluate it in the Scalus VM. This tests the actual on-chain behavior — the same code that would run on a Cardano node.

```java
var program = compileValidator(VestingValidator.class).program();
var result = evaluate(program, ctx);
assertSuccess(result);
```

You can also verify trace messages and inspect execution budgets (CPU/memory):

```java
BudgetAssertions.assertTrace(result, "Checking beneficiary");
```

## Running the Off-Chain Demo

The `VestingOffchainApp` demonstrates a full lock-and-unlock cycle against a local Cardano devnet.

### 1. Start Yaci Devkit

Install and start [Yaci Devkit](https://github.com/bloxbean/yaci-devkit):

```bash
yaci-cli:>create-node -o --era conway
yaci-cli:>start
```

Yaci Devkit runs a local Cardano node with:
- Node API on `http://localhost:8080`
- Admin API on `http://localhost:10000`

### 2. Run the Demo

Run the `main` method in `VestingOffchainApp`. You can run it directly from your IDE.

The demo will:
1. Load the pre-compiled validator using `JulcScriptLoader`
2. Create and fund a beneficiary account via Yaci Devkit
3. Lock 10 ADA to the script address with an inline datum
4. Unlock the ADA by submitting a transaction signed by the beneficiary

## Project Structure

```
julc-helloworld/
├── build.gradle                          # Dependencies and build config
├── settings.gradle
├── src/
│   ├── main/java/com/example/
│   │   ├── VestingValidator.java         # On-chain validator
│   │   ├── VestingOffchainApp.java       # Off-chain lock/unlock demo
│   │   └── util/
│   │       └── YaciHelper.java           # Yaci Devkit utilities
│   └── test/java/com/example/
│       └── VestingValidatorTest.java     # Unit tests (Java + UPLC/VM)
└── gradle/
    └── wrapper/                          # Gradle wrapper
```

## Dependencies

Defined in `build.gradle`:

| Dependency | Purpose |
|------------|---------|
| `julc-stdlib` | On-chain standard library |
| `julc-ledger-api` | ScriptContext, TxInfo, and ledger types |
| `julc-annotation-processor` | Compiles validators to UPLC during javac |
| `julc-cardano-client-lib` | Load compiled scripts at runtime |
| `cardano-client-lib` | Build and submit Cardano transactions |
| `cardano-client-backend-blockfrost` | Backend for connecting to Yaci Devkit / Blockfrost |
| `julc-testkit` | Testing utilities for validators |
| `julc-vm` + `julc-vm-scalus` | Local UPLC evaluation via Scalus VM |

## Writing Your Own Validator

1. Create a new Java class annotated with `@SpendingValidator` (or `@MintingValidator`)
2. Define your datum as a `record` inside the class
3. Add a `static boolean validate(...)` method annotated with `@Entrypoint`
4. Build with `./gradlew build` — the annotation processor compiles it to UPLC and writes `<YourValidator>.plutus.json` to `META-INF/plutus/`
5. Write tests extending `ContractTest` for both Java-level and UPLC-level testing
6. In off-chain code, call `JulcScriptLoader.load(YourValidator.class)` — it finds the `.plutus.json` on the classpath and returns a `PlutusScript` ready for transaction building

For more details on the Java subset supported by JuLC, see the [JuLC documentation](https://github.com/bloxbean/julc).

## License

MIT

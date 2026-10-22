# LogicLab

An offline Android app (Kotlin + Jetpack Compose) for learning propositional logic: solve expressions,
generate truth tables, classify propositions, evaluate step by step, apply rules of inference and
equivalence laws, and practise with generated quizzes.

## Project layout

```
app/src/main/java/com/bsit1c/
├── logic/                 Logic engine — pure Kotlin, no Android imports
│   ├── Expr.kt            Immutable AST, operators/precedence, printer (with highlight ranges)
│   ├── Parser.kt          Tokenizer + recursive-descent parser with positioned error messages
│   ├── Semantics.kt       Evaluator, truth-table generator, classifier, equivalence check
│   ├── StepEvaluator.kt   Step-by-step evaluation from the expression tree
│   ├── Structure.kt       Sub-expression breakdown / main connective
│   ├── Matcher.kt         Schema matching for rules and laws
│   ├── Inference.kt       The 9 rules of inference, argument checker, proof search
│   ├── Equivalence.kt     The 15 equivalence laws, rewriting, proof search, verification
│   └── Practice.kt        Quiz generator (answers computed by the engine) and examples
└── logiclab/              Android UI (Compose)
    ├── MainActivity.kt    Entry point and navigation
    ├── HomeScreen.kt, Module1Screens.kt, InferenceScreen.kt, EquivalenceScreen.kt, QuizScreen.kt
    ├── ExpressionEditor.kt  Expression display + on-screen logic keyboard
    ├── TruthGrid.kt       Scrollable truth-table component
    ├── Components.kt, Theme.kt, Icons.kt
    └── Storage.kt         Local data: theme, recent/saved expressions, quiz history
app/src/test/java/com/bsit1c/logic/   Unit tests for the engine (run without a device)
```

## Notation

Precedence, highest first: `¬`, `∧`, `∨`, `→`, `↔`. `∧` and `∨` group from the left, `→` groups
from the right (`P → Q → R` means `P → (Q → R)`). Variables are a letter optionally followed by digits
(`P`, `q`, `P1`). `T` and `F` are the constants true and false. ASCII input also works:
`~ ! -` (not), `& ^` (and), `| +` (or), `-> =>` (implies), `<-> <=>` (iff).

Truth tables support up to 10 distinct variables (1,024 rows).

## Running the tests

```
gradlew test
```

or right-click `app/src/test/java/com/bsit1c/logic` in Android Studio → *Run Tests*.

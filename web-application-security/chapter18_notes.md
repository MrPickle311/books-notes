# Chapter 18: Business Logic Vulnerabilities

## Overview
Business logic vulnerabilities occur when an application implements specific business rules poorly, leading to unintended outcomes. They differ from standard vulnerabilities (e.g., SQLi, XSS) because they are unique to the application's specific logic and use cases.
- **Detection**: Extremely difficult to find using automated tooling because tools lack context of the application's specific business rules.

## Types of Business Logic Vulnerabilities

### Custom Math Vulnerabilities
Occur when applications fail to validate inputs before performing mathematical operations.
- **How it works**: A user manipulates input to bypass business logic expectations, often by exploiting missing state validations (e.g., missing sufficient funds check before a transfer).
- **When to use**: Target applications that handle currency, credits, or transactions. Look for endpoints that perform additions or subtractions on user balances.
- **Prevention**: 
  - Implement appropriate mathematical operations.
  - Enforce strict validations against inputs prior to executing mathematical functions.

### Programmed Side Effects
Unintended changes resulting from the utilization of programmed functionality in unforeseen ways.
- **How it works**: Exploiting assumptions made by the application. For instance, manipulating a local liquidity pool in an exchange that incorrectly assumes local market rates will always reflect global market rates.
- **When to use**: Target complex applications with interacting features (e.g., trading platforms, marketplaces). Analyze how different features affect global application state.
- **Prevention**: Implement comprehensive edge-case detection and bounded thresholds (e.g., limiting transactions if local prices deviate significantly from global averages).

### Quasi-Cash Attacks
Vulnerabilities arising from the interaction of multiple, interdependent systems where the primary functionality allows recursive or looping financial gain.
- **How it works**: An attacker combines services to net a profit. For example, using a high-reward credit card to pay a low-fee merchant account owned by the attacker, netting the difference in cash.
- **When to use**: Look for integrations between financial intermediaries, payment gateways, and reward systems.
- **Prevention**: Implement checks and balances to detect programmatic, automated, or circular transactions (e.g., self-purchasing).

## Vulnerable Standards and Conventions
Exploiting edge cases arising from standard programming conventions, such as how programming languages handle data types.

### Numeric Precision Loss (IEEE 754)
Most major languages (JavaScript, Java, Ruby, Python) use IEEE 754 floating-point numeric format, which prioritizes memory efficiency over exact precision.
- **How it works**: Calculations involving decimal points can result in approximated values. 
  - *Example (JavaScript)*:
    You can test this using the Chrome Developer Tools (F12) Console:
    ```javascript
    0.1 + 0.2 
    // Returns: 0.30000000000000004
    ```
- **Impact**: In high-volume financial or rapidly automated calculations (multiplication, exponentiation), these microscopic precision losses can compound into significant financial discrepancies.

## Exploitation Methodology
Exploiting business logic requires domain-specific knowledge and manual testing.
1. **Familiarization**: Intimately understand the application's intended use cases from a user's perspective.
2. **Mapping**: Document every intended use case and hypothesize the backend logic/database transactions involved.
3. **Edge-Case Identification**: For each mapped functionality, identify unhandled edge cases. Examples:
   - If I purchase $1,000 of groceries in CAD, will I get 5% of that ($50) in USD or will currency conversion take place?
   - If I set up an online merchant store with PayBuddy, given the fee difference with the reward (1% versus 5%), will I be able to net 4% on zero-cost transactions?
   - If I set up an online merchant account, can I perform a negative invoice of -$1,000, adding money to my credit card balance rather than subtracting it?
   - If I purchase and then immediately refund, will I still retain the 5% reward offered?

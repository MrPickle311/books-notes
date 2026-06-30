# Chapter 36: Mitigating Business Logic Vulnerabilities

## Architecture-Level Mitigations
The most effective mitigation step occurs during the architecture phase, prior to writing application code. 

### Worst-Case Scenario Design
Traditional architectures often focus on the *intended user* and *best-case design*. Secure architectures must utilize *worst-case scenario design*, accounting for malicious use cases for every functional component.

- **How it works**: Evaluate application components assuming malicious intent and unexpected edge cases from the start, rather than relying on average or best-case metrics.
- **When to use**: During the initial architecture and design phase of any application feature.

#### Table 36-1: Algorithm A1 runtimes
| Run number | Runtime | Median runtime | Average runtime |
| :--- | :--- | :--- | :--- |
| 1 | 5 | 5 | 5 |
| 2 | 5 | 5 | 5 |
| 3 | 5 | 5 | 5 |
| 4 | 5 | 5 | 5 |
| 5 | 5 | 5 | 5 |
| 6 | 5 | 5 | 5 |
| 7 | 5 | 5 | 5 |
| 8 | 5 | 5 | 5 |
| 9 | 5 | 5 | 5 |
| 10 | 25 | 5 | 7 |
*This demonstrates how edge cases drastically alter expected performance, illustrating why architects must plan for the worst-case scenario rather than the median.*

#### Comparing Algorithms
Consider a second algorithm, A2, which runs at a constant speed of 6 (median time: 6).

Evaluating based on the median, A1 (median time: 5) appears better. However, over time A1 averages a runtime of 7, making it 16% less efficient than A2.

This is an example of **best-case design**. Had an architect chosen A1, it would perform faster than A2 until an edge case occurred. Over many iterations, edge cases slow down A1 to the point where A2 is actually faster.

Good security architects never use best-case design and always use worst-case design. By implementing worst-case scenario design, you catch business logic vulnerabilities prior to any code ever being written.

## Statistical Modeling
Combines fuzzing, data science, and browser automation to rapidly detect logic vulnerabilities. It involves modeling inputs and directional pageflows (actions) based on analytics and hypotheses.

### 1. Modeling Inputs
- Rank the most common input values by frequency (e.g., dropdowns, radio buttons, free text).
- Include uncommon and unexpected edge-case inputs (unusual characters, lengths) to test logic resilience.
- Store input models in programmatically ingestible formats (JSON, YAML, CSV, XML).

### 2. Modeling Actions
- Map all user interactions: directional pageflows, AJAX requests, websockets, real-time communication (RTC), and direct JavaScript functions.
- Utilize existing analytics tools to source action path data.

### 3. Model Development
Automate user flows programmatically in a local test environment.

- **How it works**: Populate a database with modeled users. Utilize headless browsers (e.g., Google’s Headless Chrome with Puppeteer) to simulate DOM interaction, execute JavaScript, and perform programmatic network queries based on the modeled inputs and actions.
- **When to use**: During automated security testing or QA phases to simulate complex, multi-step user interaction flows.

**Puppeteer Automation Example:**
```javascript
import puppeteer from 'puppeteer';
import data from 'model';
import tools from 'tools';

(async () => {
  const browser = await puppeteer.launch();
  const page = await browser.newPage();
  await page.goto(data.startURL);

  // Configure headless browser
  await page.setViewport({width: 1080, height: 1024});

  // Create user
  await page.type('.sign-up-username', data[0].username);
  await page.type('.sign-up-username', data[0].password);
  await page.click('.sign-up');
  await tools.logSignUpStatus()

  // Add comment when signed up
  const commentBox = '.comment-box';
  await page.waitForSelector(commentBox);
  await page.type('.comment-box', data[0].messages[0]);
  await page.click('.submit-comment');
  await tools.logCommentStatus()

  // Close browser and end automation
  await browser.close();
})();
```

### 4. Model Analysis
- **How it works**: While running the headless automation, log every network request (payload, response, HTTP status). Monitor for unexpected server errors or faults.
- **When to use**: Continuously during model execution to identify exploitable business logic flaws or general nonsecurity bugs.
- **Note**: Even if an error is found that cannot be exploited, resolving these nonsecurity bugs provides valuable insights and improves the user experience.

## Summary Notes
- Business logic vulnerabilities arise from an application's architects not considering specific edge cases or forgetting to implement proper checks in application logic.
- These vulnerabilities are unique to each application, making them difficult to attack, detect, mitigate, and remediate.
- Thinking about unintended application usage early enables secure architecture and automated defense systems. Do not neglect these vulnerabilities even if they initially appear as mere programming bugs.
- The business logic described in source code is often the most critical digital asset of a business. As applications grow in functionality, neglected logic vulnerabilities become increasingly dangerous.
- "1 minute of fixing architecture bugs saves 10 minutes of resolving implementation bugs."

# Chapter 5: Scoped Values

*He who does not place things in their proper place has committed injustice.*
—Ali ibn Abi Talib

In this chapter, we explore `ScopedValue`, a powerful addition to Java (finalized in JDK 25) that provides a structured way to bind values to a specific scope while remaining accessible and consistent with the context. Unlike traditional `ThreadLocal` variables—which can be cumbersome and prone to memory leaks—`ScopedValue` offers a cleaner and more efficient approach for context propagation in multi-threaded applications.

## The Burden of Passing Context

There are often scenarios where data needs to be shared among various parts of the code, but simply using method arguments isn't feasible. This is especially common when an application depends on a framework.

To illustrate, consider a job-scheduling framework where user code registers tasks for execution. Whenever the framework runs a job, it creates a `JobContext` containing metadata (e.g., job name, priority, scheduling constraints). This context is essential for framework operations but largely irrelevant to user code.

The following example demonstrates a typical job-scheduling framework that suffers from the **"parameter passing problem"**:

```java
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

interface Job {
    void execute(JobContext context);
}

enum Priority { LOW, MEDIUM, HIGH }

public record JobContext(String jobName, Priority priority, 
                         Map<String, Object> metadata) {
    public JobContext(String jobName, Priority priority) {
        this(jobName, priority, new HashMap<>());
        metadata.put("jobName", jobName);
        metadata.put("priority", priority);
        metadata.put("creationTime", Instant.now());
    }
    
    public Object getMetadataValue(String key) {
        return metadata.get(key);
    }
}
```

The framework's core scheduling logic creates and manages the job context:

```java
// Framework code
public class JobScheduler {
    public void schedule(Job job, String jobName, Priority priority) {
        JobContext context = new JobContext(jobName, priority);
        runJob(job, context);
    }
    
    private void runJob(Job job, JobContext context) {
        // The framework calls user code here, passing the context
        job.execute(context);
    }
    
    public Object getJobMetadata(String key, JobContext context) {
        if (context == null) {
            return null;
        }
        return context.getMetadataValue(key);
    }
}
```

To use this framework, we must implement the `Job` interface and thread the `context` parameter through our code:

```java
public class UserJob implements Job {
    private final JobScheduler jobScheduler;
    
    public UserJob(JobScheduler jobScheduler) {
        this.jobScheduler = jobScheduler;
    }
    
    @Override
    public void execute(JobContext context) {
        System.out.println("User job is running!");
        
        // User code calls back into the framework to retrieve metadata
        Object creationTime = jobScheduler.getJobMetadata("creationTime", context);
        System.out.println("Job creation time: " + creationTime);
        
        // Any helper methods also need the context parameter
        processJobData(context);
    }
    
    private void processJobData(JobContext context) {
        // Even though this method might not directly use context,
        // it needs the parameter to pass to other framework methods
        Object priority = jobScheduler.getJobMetadata("priority", context);
        System.out.println("Processing job with priority: " + priority);
    }
}
```

While this approach functions correctly, it introduces several architectural problems that become pronounced as applications scale:

### Parameter Pollution
The `JobContext` is primarily a framework construct. However, because the framework must manage its internal context across the call chain—from `schedule()` down into user code in `execute()`, and back into the framework inside `getJobMetadata()`—user code is forced to carry around `JobContext` parameters. Every method in the execution flow must declare this parameter, polluting method signatures with framework details that have no business meaning to the application logic.

### Interface Brittleness
Consider what happens when the framework evolves. If `JobContext` needs to carry a distributed tracing context or a logging reference, every method signature in the user code passing that context around might need modification. Any expansion of the framework's needs ripples through the entire user codebase.

### Coupling and Testability
User code becomes tightly coupled to framework implementation details. Testing individual methods becomes complex because a valid `JobContext` must always be provided, even for tests focusing on business logic completely unrelated to the framework. This also makes migrating to different frameworks or extracting business logic for reuse significantly harder.

---

## Introducing ThreadLocal

To circumvent this problem, the framework code can be designed intelligently using `ThreadLocal`, a tool historically used to resolve parameter pollution.

Let's reimplement the preceding framework code using `ThreadLocal`:

```java
public class JobScheduler {
    private static final ThreadLocal<JobContext> jobContextHolder = 
        new ThreadLocal<>();
        
    public void schedule(Job job, String jobName, Priority priority) {
        JobContext context = new JobContext(jobName, priority);
        try {
            // Sets the context in the current thread before executing the job
            jobContextHolder.set(context);
            runJob(job);
        } finally {
            // Always remove the context in a finally block to prevent memory leaks
            jobContextHolder.remove();
        }
    }
    
    private void runJob(Job job) {
        // The runJob method no longer needs context parameters
        job.execute();
    }
    
    public Object getJobMetadata(String key) {
        // Framework methods retrieve context from ThreadLocal whenever needed
        JobContext context = jobContextHolder.get();
        return (context != null) ? context.getMetadataValue(key) : null;
    }
}
```

Now the user code becomes much cleaner, with no need to handle framework context whatsoever:

```java
public class UserJob implements Job { // Assume Job interface execute() takes 0 args now
    private final JobScheduler jobScheduler;
    
    public UserJob(JobScheduler jobScheduler) {
        this.jobScheduler = jobScheduler;
    }
    
    @Override
    public void execute() {
        System.out.println("User job is running!");
        
        // No context parameter needed - framework handles it internally
        Object creationTime = jobScheduler.getJobMetadata("creationTime");
        System.out.println("Job creation time: " + creationTime);
        
        // Helper methods are now clean
        processJobData();
    }
    
    private void processJobData() {
        // Clean method signature - no framework parameters
        Object priority = jobScheduler.getJobMetadata("priority");
        System.out.println("Processing job with priority: " + priority);
    }
}
```

This approach solves the parameter-passing problem effectively:
1. User code is completely freed from framework context concerns.
2. Framework services are accessible without any context parameters.
3. Helper methods have clean signatures focused solely on business logic.

This pattern is so effective that virtually all modern frameworks use some form of `ThreadLocal` for context management. Spring Framework, for example, uses `ThreadLocal` extensively for security contexts, transaction contexts, and request contexts.

---

## Limitations of ThreadLocal Variables

Although `ThreadLocal` seems intelligent and useful, it has several inherent design flaws that make it problematic—especially in the age of massive-scale concurrency with Virtual Threads. Let's discuss them.

### 1. Unconstrained Mutability
`ThreadLocal` offers unconstrained mutability. Any code that can call `get()` on a `ThreadLocal` can also call `set()`, allowing the data to change at any time. This makes it extremely difficult to track when and where data is modified.

```java
public class MutableLoggingContext {
    // A ThreadLocal holding the current log level
    private static final ThreadLocal<String> LOG_LEVEL = new ThreadLocal<>();
    
    public static void setLogLevel(String level) {
        LOG_LEVEL.set(level); // 1. Any code can access this
    }
    
    public static String getLogLevel() {
        return LOG_LEVEL.get();
    }
    
    public static void log(String message) {
        System.out.println("[" + getLogLevel() + "] " + message);
    }
    
    public static void main(String[] args) throws InterruptedException {
        setLogLevel("INFO"); // 3. Main thread sets its log level to INFO
        log("Starting process...");
        
        Thread thread = new Thread(() -> {
            setLogLevel("DEBUG"); // 4. Child thread independently mutates it
            log("Thread-specific debug mode enabled");
        });
        thread.start();
        Thread.sleep(100);
        
        // 5. The main thread's log level remains INFO (thread-local isolation)
        log("Main thread still at INFO level");
    }
}
```
Setting the log level can be done anywhere, leading to confusion about where it was originally set.

### 2. Unbounded Lifetime and Memory Leaks
`ThreadLocal` offers an unbounded lifetime. Once a thread-local variable is set, it remains for the life of that thread unless explicitly removed via `remove()`. Since modern applications rely on thread pools where the same threads are reused repeatedly, failing to call `remove()` leads to data leaking from one task to another, causing memory leaks and security vulnerabilities.

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadLocalLeakExample {
    private static final ThreadLocal<String> currentUser = new ThreadLocal<>();
    
    public static void main(String[] args) throws InterruptedException {
        // 1. Single-thread pool ensures the same thread handles both tasks
        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
            
            // The first task sets the current user
            executor.submit(() -> {
                currentUser.set("Alice"); // 2. First task sets a value
                System.out.println("Task 1: currentUser = " + currentUser.get());
                // 3. Missing cleanup! Forgot to call currentUser.remove()
            });
            
            Thread.sleep(100);
            
            // The second task reuses the same thread
            executor.submit(() -> {
                // 4. Second task sees the leaked value from the first task!
                System.out.println("Task 2: Leaked value = " + currentUser.get());
                currentUser.set("Bob");
                System.out.println("Task 2: currentUser = " + currentUser.get());
                currentUser.remove(); 
            });
        }
    }
}
```
This can cause security vulnerabilities if sensitive data (like authentication tokens) leaks between unrelated HTTP requests being handled by the same worker thread.

### 3. Expensive Inheritance Overhead
When using `InheritableThreadLocal`, child threads automatically inherit values from their parent thread. While this is convenient, it becomes extremely expensive in terms of memory and performance when spinning up large numbers of child threads:

```java
public class InheritanceOverheadExample {
    private static final InheritableThreadLocal<byte[]> LARGE_DATA =
        new InheritableThreadLocal<>();
        
    public static void main(String[] args) {
        // Parent thread sets a large 10MB object
        LARGE_DATA.set(new byte[10_000_000]); 
        
        // Create 100 child threads
        for (int i = 0; i < 100; i++) {
            new Thread(() -> {
                // Each child thread automatically gets its own copy of the parent's data
                byte[] inherited = LARGE_DATA.get();
                System.out.println("Child has access to " + inherited.length + " bytes");
            }).start();
        }
    }
}
```
This example clearly demonstrates the downside of `InheritableThreadLocal`. Even though the child threads do not explicitly alter the local variable, they automatically inherit their own separate copy of the parent's data, resulting in massive, unintended duplicate memory usage.

---

## Toward Lightweight Sharing

The limitations of `ThreadLocal` become glaringly apparent with the introduction of Virtual Threads (JEP 444). Unlike traditional platform threads—where each thread has its own dedicated OS resource—virtual threads allow a single OS thread to host millions of lightweight threads.

While it is technically possible for each virtual thread to maintain its own `ThreadLocal` data, the memory overhead quickly becomes unsustainable. Imagine a million virtual threads, each carrying its own massive chunk of state.

We need a new mechanism that allows us to store inheritable, per-thread data without incurring numerous copies. If the data is **immutable**, we can have one shared version referenced by all child threads without duplication. We also need a **bounded lifetime**; once the task is finished, the attached thread locals should lose their relevance rather than act as a perpetual stash of memory.

That's where the new `ScopedValue` API comes into the picture.

---

## Core Components of ScopedValue

A `ScopedValue` acts as an implicit method parameter, allowing data to be passed through a sequence of method calls without explicitly declaring it in each method's signature. 

It has three main characteristics:
1. **Immutability**: Once a `ScopedValue` is bound to a value within a specific scope, it cannot be altered.
2. **Thread-scoped binding**: Bindings are confined to the current thread, preventing unintended data sharing.
3. **Bounded lifetime**: The binding of a `ScopedValue` is strictly limited to the duration of a specific code block, aiding in resource management and completely eliminating memory leaks.

> [!NOTE]
> `ScopedValue` was finalized in JDK 25. If you are using an older JDK (like JDK 24), you must enable it using the `--enable-preview` flag during compilation and runtime.

To use a scoped value, we first declare it as a static final field, without the `new` operator:
```java
private static final ScopedValue<String> NAME = ScopedValue.newInstance();
```

We bind a value to the scoped value and execute code within that scope using the `where()` and `run()` methods:
```java
ScopedValue.where(NAME, "duke").run(() -> doSomething());

// Inside doSomething() or any nested method call:
String user = NAME.get();
```

### Re-implementing JobScheduler with ScopedValue

If we now replace the `JobScheduler` class to use `ScopedValue`, it looks like this:

```java
public class JobScheduler {
    // 1. Creates a ScopedValue instance using the factory method
    private static final ScopedValue<JobContext> CONTEXT = ScopedValue.newInstance();
    
    public void schedule(Job job, String jobName, Priority priority) {
        JobContext context = new JobContext(jobName, priority); // 2. Context object
        
        // 3. Binds the context and executes the job WITHIN that scope
        ScopedValue.where(CONTEXT, context)
                   .run(() -> runJob(job));
    }
    
    private void runJob(Job job) {
        job.execute(); // 4. The job executes with access to the scoped context
    }
    
    public static JobContext getContext() {
        // 5. Provides static access to the current context
        return CONTEXT.get();
    }
    
    public static Object getJobMetadata(String key) {
        JobContext context = CONTEXT.get(); // 6. Retrieves metadata safely
        if (context != null) {
            return context.getMetadataValue(key);
        }
        return null;
    }
}
```

### Returning values with `call()`
While `run()` executes a `Runnable` (returns void), `ScopedValue` also provides `call()` to execute a `Callable` when you need to return a value from the scoped execution:

```java
public class PricingService {
    private static final ScopedValue<Double> DISCOUNT_RATE = ScopedValue.newInstance();
    
    public double calculatePrice(double basePrice) {
        // Using call() to return the calculated price from within the scope
        return ScopedValue.where(DISCOUNT_RATE, 0.20) // 20% discount
                          .call(() -> basePrice * (1 - DISCOUNT_RATE.get()));
    }
}

void main() {
    PricingService service = new PricingService();
    double finalPrice = service.calculatePrice(100.0);
    System.out.println("Final price: $" + finalPrice); // Output: Final price: $80.00
}
```

---

## Running ScopedValue

`ScopedValue` has a bounded lifetime. We must first set it, and then we can use it.

Consider the following example. If we run a task without binding the scope first, it prints that the value is unbound:

```java
public static void main(String[] args) {
    ScopedValue<String> NAME = ScopedValue.newInstance();
    
    Runnable task = () -> {
        if (NAME.isBound()) {
            System.out.println("Name is bound: " + NAME.get());
        } else {
            System.out.println("Name is not bound");
        }
    };
    
    task.run(); // Output: Name is not bound
}
```

To bind a value and execute code within that scope, we use the `where()` and `run()` methods:

```java
// Binds "Bazlur" to NAME, and executes the task within that binding's scope
ScopedValue.where(NAME, "Bazlur").run(task); 
// Output: Name is bound: Bazlur
```

What happens if we execute the task inside the scope, and then try again outside?

```java
// Execute within scope
ScopedValue.where(NAME, "Bazlur").run(task); // Output: Name is bound: Bazlur

// Try to execute outside scope
task.run(); // Output: Name is not bound
```
The `ScopedValue` only remains bound within the **dynamic scope** of the `run()` method call. Once that method completes, the binding is automatically removed, ensuring clean scope boundaries and preventing value leakage between unrelated code sections.

> [!NOTE]
> **Dynamic Scope vs. Lexical Scope**
> In Java, "scope" usually refers to *lexical scope* (defined by `{}` blocks) where variables are accessible only within their declared text boundaries. However, `ScopedValue` operates on a *dynamic scope*, which is determined by the program's **execution flow**.
> Dynamic scope means a value is accessible during the execution of specific methods and the methods they call, directly or indirectly. For example, if `a` calls `b`, and `b` calls `c`, the scope flows through `c` but ends when `c` finishes. This temporary and precise scoping is what makes `ScopedValue` safer than `ThreadLocal`.

### Scoped Values and New Threads

Can we run the task in another thread? Consider the following code:

```java
public static void main(String[] args) throws InterruptedException {
    ScopedValue<String> NAME = ScopedValue.newInstance();
    Runnable task = () -> { /* prints if NAME is bound */ };
    
    // Creates an unstarted platform thread with our task
    Thread thread = Thread.ofPlatform().unstarted(task);
    
    // Binds the scoped value and starts the thread WITHIN that scope
    ScopedValue.where(NAME, "Bazlur").run(thread::start);
    
    thread.join();
}
```
**Output**: `Name is not bound`

Why? Because scoped values are **not automatically inherited by newly created threads**. Although `thread::start` executes within the scope where `NAME` is bound, the actual task runs in a separate thread that doesn't inherit the scoped value binding. This behavior is the same for both platform and virtual threads.

To fix this, we must bind the `ScopedValue` *inside* the newly created thread's execution:

```java
Thread thread = Thread.ofVirtual().start(() -> {
    ScopedValue.where(NAME, "Bazlur").run(task);
});
thread.join();
```
Now it correctly prints `Name is bound: Bazlur`.

### Fluent API for Multiple Bindings

The `ScopedValue` provides a fluent API. Using it, we can bind multiple `ScopedValue` chains together in a single statement:

```java
public class MultiScopedExample {
    private static final ScopedValue<String> USER_ID = ScopedValue.newInstance();
    private static final ScopedValue<String> SESSION_ID = ScopedValue.newInstance();
    
    public static void main(String[] args) {
        // 1. Binds the first scoped value
        // 2. Chains another binding
        // 3. Executes code with both values bound
        ScopedValue.where(USER_ID, "user123")
                   .where(SESSION_ID, "session456")
                   .run(() -> performTask());
    }
    
    public static void performTask() {
        // 4. Allows both values to remain accessible throughout the call stack
        System.out.println("Performing task for user: " + USER_ID.get() +
                           " in session: " + SESSION_ID.get());
    }
}
```

### Safe Access and Default Values

`ScopedValue` provides two convenient methods, `orElse` and `orElseThrow`, which are useful when you want to safely handle unbound states without throwing `NoSuchElementException`.

```java
public class ScopedValueDefaultsExample {
    private static final ScopedValue<String> USER_NAME = ScopedValue.newInstance();
    
    public static void main(String[] args) {
        // 1. Provide safe access with a default value when unbound
        String userNameUnbound = USER_NAME.orElse("Guest");
        System.out.println("No binding -> user name defaults to: " + userNameUnbound);
        
        // 2. Allow for validation that throws a custom exception when unbound
        try {
            USER_NAME.orElseThrow(() -> 
                new IllegalStateException("No user name bound yet!"));
        } catch (IllegalStateException e) {
            System.out.println("Caught exception: " + e.getMessage());
        }
        
        // Within a bound scope, these just return the bound value
        ScopedValue.where(USER_NAME, "Bazlur").run(() -> {
            String boundUserName = USER_NAME.orElse("Guest");
            System.out.println("Within binding -> user name is: " + boundUserName);
        });
    }
}
```

---

## Rebinding ScopedValue in Nested Scopes

One of the most powerful features of `ScopedValue` is its ability to *rebind* within nested scopes. Rebinding allows you to assign a new value to the exact same `ScopedValue` for a limited duration, entirely confined to a specific subscope. Once the subscope finishes executing, the original value is automatically restored.

This rebinding feature is particularly useful in scenarios like role-based access control (where a user's role can temporarily switch for a specific operation) or for task-specific configuration overrides.

Let's look at this in action:

```java
public class ScopedValueRebindingExample {
    private static final ScopedValue<String> USER_ROLE = ScopedValue.newInstance();
    
    public static void main(String[] args) {
        // 1. Establishes the initial binding of "Admin" in the outer scope
        ScopedValue.where(USER_ROLE, "Admin").run(() -> {
            System.out.println("Outer scope: User role is " + USER_ROLE.get());
            performTask();
            
            // 2. Creates a nested scope that temporarily rebinds the value to "Guest"
            ScopedValue.where(USER_ROLE, "Guest").run(() -> {
                System.out.println("Inner scope: User role is " + USER_ROLE.get());
                performTask();
            });
            
            // 3. When the inner scope exits, the original "Admin" value is restored!
            System.out.println("Back to outer scope: User role is " + USER_ROLE.get());
            performTask();
        });
    }
    
    public static void performTask() {
        // 4. The identical method accesses different values based on the current scope
        System.out.println(" Performing task as: " + USER_ROLE.get());
    }
}
```

If we run this code, we get the following output:
```text
Outer scope: User role is Admin
 Performing task as: Admin
Inner scope: User role is Guest
 Performing task as: Guest
Back to outer scope: User role is Admin
 Performing task as: Admin
```

Because `ScopedValue` bindings are completely immutable during their lifecycle, this rebinding mechanism is perfectly safe and ensures clean, predictable context management without side effects.

---

## ScopedValue and Structured Concurrency

`ScopedValue` is designed to work seamlessly with structured concurrency. As we have seen in previous examples, `ScopedValue` bindings are **not** inherited by arbitrary child threads created via `Thread.start()`. However, when used within a `StructuredTaskScope`, `ScopedValue` bindings are **automatically inherited** by all child threads forked within that scope.

This inheritance mechanism facilitates efficient, implicitly passed data sharing between parent and child threads. It is absolutely safe because structured concurrency has well-defined boundaries: when we exit the `StructuredTaskScope`, all threads created inside the scope are terminated and garbage collected, completely eliminating the risk of memory leaks.

Let's look at an example:

```java
import java.util.concurrent.StructuredTaskScope;

public class ScopedValueStructuredConcurrencyExample {
    private static final ScopedValue<String> USERNAME = ScopedValue.newInstance();
    
    public static void main(String[] args) {
        // 1. Bind the value in the parent thread
        ScopedValue.where(USERNAME, "Bazlur").run(() -> {
            doSomething();
        });
    }
    
    public static void doSomething() {
        // 2. Open a StructuredTaskScope while inside the scoped value's binding
        try (var scope = StructuredTaskScope.open()) {
            
            // 3. Child tasks automatically inherit the binding without explicit passing!
            StructuredTaskScope.Subtask<String> task1 = scope.fork(()
                -> USERNAME.get() + " from task 1");
                
            StructuredTaskScope.Subtask<String> task2 = scope.fork(()
                -> USERNAME.get() + " from task 2");
                
            scope.join();
            
            System.out.println(task1.get()); // Output: Bazlur from task 1
            System.out.println(task2.get()); // Output: Bazlur from task 2
            
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
```

In this example, the `USERNAME` scoped value is bound to the string `"Bazlur"` in the main thread. When `doSomething()` is called, it creates a `StructuredTaskScope` and forks two child virtual threads. Because the child threads were created via `scope.fork()`, they seamlessly inherit the `USERNAME` binding from the parent thread and can successfully access it using `USERNAME.get()`.

---

## Performance Considerations

`ScopedValue` generally shows better performance compared to `ThreadLocal`, especially when working with virtual threads. This performance advantage is the result of several factors:

1. **Reduced Overhead**: `ThreadLocal` variables can introduce significant overhead when each virtual thread needs its own copy, which drives up memory consumption. In contrast, `ScopedValue` lets us share immutable data among threads within a defined scope, minimizing memory usage and boosting performance.
2. **Optimized for Virtual Threads**: `ScopedValue` is highly optimized for virtual threads and structured concurrency. It leverages the lightweight nature of virtual threads to provide efficient data sharing without the usual bottlenecks of traditional thread-local variables. 

For example, consider a web server that spins up thousands of virtual threads to handle concurrent requests. Each request may need contextual data like user authentication details. By using `ScopedValue` to share this data within each request's scope, we significantly reduce memory consumption and improve overall throughput compared to using `ThreadLocal`.

---

## Usability and API Design

Beyond performance, `ScopedValue` brings several usability advantages that make it a far better alternative to `ThreadLocal`:

1. **Enforced Immutability**: `ScopedValue` enforces immutability; once a value is associated within a scope, it cannot be modified. This simplifies reasoning about the code and completely prevents race conditions and data inconsistencies that often arise when multiple threads attempt to modify shared variables via `.set()`.
2. **Explicit Lifecycle**: `ScopedValue` explicitly defines the lifecycle of shared data through its API design. The `run()` method clearly marks the lexical and dynamic boundaries where the value is accessible. Unlike the implicit behavior of `ThreadLocal`, this design ensures a clearer boundary for where the value is valid.
3. **Concise API**: The API is far more intuitive. The `.where()` and `.run()` methods provide a structured way to set and access shared data, compared to the often verbose and less straightforward methods of `ThreadLocal`.
4. **Capability Object**: `ScopedValue` acts as a capability object. By declaring the `ScopedValue` object with access modifiers like `private static final`, it becomes possible to restrict access to authorized components only, adding a strict layer of encapsulation.
5. **Robust Null Handling**: `ScopedValue` handles `null` values more explicitly. With `ThreadLocal`, calling `get()` returns `null` whether the value was explicitly set to `null` or never set at all. In contrast, if a `ScopedValue` is unbound, calling `get()` throws a `NoSuchElementException`, ensuring any oversight is caught early.

---

## Migrating to Scoped Values

Scoped values are highly preferable in many scenarios where thread-local variables are used today. Beyond serving as hidden method parameters, scoped values can be particularly useful in areas such as recursion tracking and flattened transactions.

### Recursion Detection
Sometimes we want to detect recursion, perhaps because a framework is not re-entrant or to prevent infinite loops from malicious inputs (e.g., recursive templates). 

A scoped value can model a recursion counter by being repeatedly rebound with incremented values deep in the call stack:

```java
public class TemplateProcessor {
    private static final ScopedValue<Integer> RECURSION_DEPTH = ScopedValue.newInstance();
    private static final int MAX_NESTING_LEVEL = 50;
    
    public String processTemplate(String template) {
        if (!RECURSION_DEPTH.isBound()) {
            // 1. Establish the base recursion depth
            return ScopedValue.where(RECURSION_DEPTH, 0)
                              .call(() -> processTemplateInternal(template));
        } else {
            return processTemplateInternal(template);
        }
    }
    
    private String processTemplateInternal(String template) {
        int currentDepth = RECURSION_DEPTH.get();
        if (currentDepth >= MAX_NESTING_LEVEL) {
            throw new RuntimeException("Template nesting too deep: " + currentDepth);
        }
        
        // ... string processing logic ...
        
        // 2. Rebind the depth counter for the nested template evaluation
        String nestedContent = ScopedValue.where(RECURSION_DEPTH, currentDepth + 1)
                                          .call(() -> processTemplateInternal(loadTemplate(includePath)));
                                          
        // ...
        return result.toString();
    }
}
```

### Flattened Transactions
Detecting recursion is also useful for flattened transactions, where any transaction started while another is in progress simply joins the outermost transaction:

```java
public class FlattenedTransactionExample {
    private static final ScopedValue<Transaction> CURRENT_TRANSACTION = ScopedValue.newInstance();
    
    private static void performNestedOperation() {
        if (CURRENT_TRANSACTION.isBound()) {
            // Join existing transaction
            Transaction currentTx = CURRENT_TRANSACTION.get();
            System.out.println("Joining existing transaction: " + currentTx.name());
            performDatabaseOperation();
        } else {
            // Start new transaction if none exists
            Transaction newTx = new Transaction("NESTED_TX");
            ScopedValue.where(CURRENT_TRANSACTION, newTx).run(() -> {
                System.out.println("Starting new transaction: " + newTx.name());
                performDatabaseOperation();
            });
        }
    }
    // ...
}
```

### Shared Graphics Contexts
Scoped values are also excellent for managing shared contexts that require automatic cleanup and reentrancy, such as graphics drawing contexts (colors, line widths):

```java
public class SimpleGraphicsExample {
    private static final ScopedValue<Color> DRAW_COLOR = ScopedValue.newInstance();
    private static final ScopedValue<Integer> LINE_WIDTH = ScopedValue.newInstance();
    
    static void drawButton(String label) {
        // Button uses blue color with thick border
        ScopedValue.where(DRAW_COLOR, Color.BLUE)
                   .where(LINE_WIDTH, 3)
                   .run(() -> {
                       drawRectangle("button-background");
                       
                       // Text inside button temporarily overrides color to white
                       ScopedValue.where(DRAW_COLOR, Color.WHITE)
                                  .where(LINE_WIDTH, 1)
                                  .run(() -> System.out.println("Drawing text: " + label));
                                  
                       // Automatically reverts to blue border
                       drawRectangle("button-border");
                   });
    }
}
```

When migrating, evaluate carefully:
- **Immutability requirement**: Ensure the data you intend to share is immutable. If your use case strictly requires mutable context data, `ScopedValue` might not be the right fit.
- **Null handling**: Be prepared to handle `NoSuchElementException` when unbound, instead of checking for `null` like you would with `ThreadLocal`.

---

## In Closing

`ScopedValue` offers a cleaner, safer, and more efficient alternative to `ThreadLocal` for sharing context across a call chain. By being immutable and bounded to a well-defined dynamic scope, it addresses many of `ThreadLocal`'s shortcomings: unconstrained mutability, unbounded lifetime, and massive memory overhead.

Its lightweight nature aligns perfectly with virtual threads and structured concurrency, drastically reducing resource usage when spinning up huge numbers of concurrent tasks. The explicit syntax of `where()` and `run()` makes it abundantly clear when and where a given value is in effect, boosting code readability and maintainability.

As of JDK 25, `ScopedValue` is a stable API, fully ready for production use. It stands as the recommended solution for context propagation in modern Java applications.

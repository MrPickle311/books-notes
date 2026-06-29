# Chapter 33: Defending Data and Objects

## Overview
Data and objects interact within application code typically stored either ephemerally (in-memory) or persistently (in-filesystem). Since operations primarily occur in memory, defenses that benefit ephemeral data often extend to persistent data.

## Defending Against Mass Assignment
Mass assignment vulnerabilities occur when an application trusts client-provided data and updates database fields without restricting which fields can be modified. 

**Vulnerable Example:**
```javascript
/*
* This is a server-side API endpoint for updating player data
* for the web-based video game "MegaGame".
*/
app.post("updatePlayerData", function(req, res, next) {
  // if client sent back player state data, update in the database
  if (!!req.body.data) {
    db.update(session.currentUser, req.body.data);
    return res.sendStatus(200); // success
  } else {
    return res.sendStatus(400); // error
  }
});
```

### Mitigation Strategies

#### 1. Validation and Allowlisting
* **How it works:** Restricts fields accepted from the client by defining an explicit allowlist. Any field not on the allowlist is ignored or rejected prior to the database update call (e.g., ensuring that mass assignment of a sensitive field like `admin` is not possible).
* **When to use:** Easiest method to implement; best for simple objects where explicit fields are well-defined.

**Example:**
```javascript
const allowlist = ["hp", "location"]; // only allow these two fields to be updated
```

#### 2. Data Transfer Objects (DTOs)
* **How it works:** Creates an intermediary object (DTO) for passing data between services or functions. Incoming data passes through the DTO constructor, automatically dropping any extraneous parameters.
* **When to use:** Intensive but robust mitigation, best for complex systems or when passing structured data between services.

**Example:**
```javascript
const DTO = function(hp, location) {
  this.hp = hp;
  this.location = location;
};
```

## Defending Against Insecure Direct Object Reference (IDOR)
Objects and files should never be referenced directly or via easily guessable API structures.

### Mitigation Strategies
*   **Masking and Authorization:** Mask the API call to hide the specific filename and perform strict authorization checks before returning the file. This is the optimal long-term solution.
*   **Randomized References:** If masking is impossible, use randomly generated filenames and object references with high entropy. 
    *   *When to use:* Typically a short-term mitigation until long-term architecture changes are feasible.
    *   *Requirement:* Must be combined with other mechanisms (e.g., rate limiting) to prevent brute-forcing, requiring millions of guesses to find another file.

## Defending Against Serialization Attacks
Serialization attacks stem from weak serialization practices. 

### Mitigation Strategies
*   **Use Strong Libraries:** Rely on well-tested, open-source serialization/deserialization libraries with millions of users and a history of security audits.
*   **Choose Popular Formats:** Prefer established formats like JSON or YAML over obscure formats with little track record.
*   **Sanitize Data:** Data potentially interpreted as scripts by servers/browsers or containing common escape characters must be sanitized (by the serializer or a secondary function) prior to serialization and deserialization.
*   **Allowlist Characters/Types:** If the serializer lacks robust built-in character handling, manually allowlist input characters and object types to reduce risk probability.

## Summary
While attacks against complex objects and data exist, and appear to be a fault of the programming languages of the web, they are often easy to defend against. Being prepared to design a web application with such data and object attacks in mind, and understanding the risks, should give you peace of mind because mitigations are readily accessible for these types of issues.

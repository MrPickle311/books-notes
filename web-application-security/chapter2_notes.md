# Chapter 2: Introduction to Web Application Reconnaissance

## Core Concepts
- **Web Application Reconnaissance**: The explorative data-gathering phase before attacking an application. Used by hackers, pen testers, and security engineers to uncover vulnerabilities and map out functionality.
- **Goal**: Develop a deep technical understanding of the application's structure, APIs, network exposure, and features.
- **Warning**: Recon techniques can flag your IP or result in bans. Only perform against authorized targets.

## Access Levels and RBAC
- Users rarely have access to the *entire* application UI.
- **Role-Based Access Control (RBAC)**: Most applications employ tiered permissions structures.
  - *Example*: External customers have read-only or limited write access, while internal staff (e.g., tellers, bankers, admins, moderators) have elevated privileges to modify, create, and delete records.

### Banking Application RBAC Example
| User | Type | Permissions |
| --- | --- | --- |
| Customer | External | Log in to website. Read account balance via web UI. |
| Teller | Internal | Create new accounts when provided paperwork from a customer. |
| Banker | Internal | Modify existing accounts on behalf of customers. |

- **Objective**: Use recon to discover hidden API endpoints intended for elevated users, potentially bypassing UI restrictions to find permission glitches.

## Target Architecture and Mapping
- Recon aids in identifying internal network structures, network-accessible file servers, and unprotected APIs caused by misconfigured firewalls or servers.
- **Web Application Mapping**: The systematic collection of data points (topography) regarding code, network structure, and features to build a comprehensive view.
- **Why it matters**: Mapping allows testers to prioritize attacks by identifying heavily secured areas vs. potentially weak entry points.

## Documentation and Note-Taking
- Effective reconnaissance requires robust and scalable documentation that preserves relationships and hierarchies.
- **Recommended Format**: JSON-like hierarchical structures, mind-mapping software (e.g., XMind), or hierarchical note-taking apps (e.g., Notion).

### Example JSON-like API Map
```json
{
  "api_endpoints": {
    "sign_up": {
      "url": "mywebsite.com/auth/sign_up",
      "method": "POST",
      "shape": {
        "username": { "type": "String", "required": true, "min": 6, "max": 18 },
        "password": { "type": "String", "required": true, "min": 6, "max": 32 },
        "referralCode": { "type": "String", "required": false, "min": 64, "max": 64 }
      }
    },
    "sign_in": {
      "url": "mywebsite.com/auth/sign_in",
      "method": "POST",
      "shape": {
        "username": { "type": "String", "required": true, "min": 6, "max": 18 },
        "password": { "type": "String", "required": true, "min": 6, "max": 32 }
      }
    },
    "reset_password": {
      "url": "mywebsite.com/auth/reset",
      "method": "POST",
      "shape": {
        "username": { "type": "String", "required": true, "min": 6, "max": 18 },
        "password": { "type": "String", "required": true, "min": 6, "max": 32 },
        "newPassword": { "type": "String", "required": true, "min": 6, "max": 32 }
      }
    }
  },
  "features": {
    "comments": {},
    "uploads": {
      "file_sharing": {}
    }
  },
  "integrations": {
    "oauth": {
      "twitter": {},
      "facebook": {},
      "youtube": {}
    }
  }
}
```

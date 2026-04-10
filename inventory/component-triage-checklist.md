# Component Triage Checklist

Complete one copy of this checklist per low-side component,
working with the ADMS on-site team (e.g. John Isted) before any
containerisation work begins on that component.

---

## Component: ___________________________  ID: BF-LOW-___

### 1. Deployment Status

- [ ] Still deployed in **non-prod** environment?  Yes / No / Unknown
- [ ] Still deployed in **target (prod)** environment?  Yes / No / Unknown
- [ ] Has it been undeployed / deleted?  Yes / No / Unknown
- [ ] Is it still actively called / receiving traffic?  Yes / No / Unknown

**Decision: Include in containerisation scope?**  Yes / No  
*If No — mark as retired in app-inventory.json and stop here.*

---

### 2. Source Code

- [ ] Source code located in SCM at: ______________________________
- [ ] Last commit / change date: ____________________
- [ ] Build script confirmed and working?  Yes / No
- [ ] Last successful clean build date: ____________________
- [ ] Any outstanding patches / hot-fixes NOT in SCM?  Yes / No
  - If Yes, describe: ________________________________________

---

### 3. Runtime Environment (Current)

| Item | Dev (ATOM WS2008 R2) | Non-Prod Target | Prod Target |
|------|----------------------|-----------------|-------------|
| OS | Windows Server 2008 R2 | ____________ | ____________ |
| Java version | ____________ | ____________ | ____________ |
| .NET version | ____________ | ____________ | ____________ |
| C++ runtime (MSVCRT) | ____________ | ____________ | ____________ |
| Tomcat version | ____________ | ____________ | ____________ |
| IIS version | ____________ | ____________ | ____________ |
| Service wrapper | ____________ | ____________ | ____________ |
| Service wrapper version | ____________ | ____________ | ____________ |
| Oracle client version | ____________ | ____________ | ____________ |

---

### 4. Interface Contracts (Critical — must be preserved)

#### Upstream interfaces (what sends data TO this component)

| # | Component name | Side | Protocol | Port | Message format | Auth method |
|---|---------------|------|----------|------|---------------|-------------|
| 1 | | | | | | |
| 2 | | | | | | |

#### Downstream interfaces (what this component sends data TO)

| # | Component name | Side | Protocol | Port | Message format | Auth method |
|---|---------------|------|----------|------|---------------|-------------|
| 1 | | | | | | |
| 2 | | | | | | |

**Are any of the above interfaces to HIGH-SIDE components?**  Yes / No  
*If Yes — these interface contracts are FIXED. The containerised component must preserve them byte-for-byte.*

---

### 5. Configuration & Secrets

- [ ] Config files identified and listed:
  - ________________________________________
  - ________________________________________
- [ ] Any config differences between environments (dev / non-prod / prod)?  Yes / No
  - If Yes, describe: ________________________________________
- [ ] Credentials / secrets externalised (not hardcoded)?  Yes / No
  - If No — plan externalisation to env vars / secret store
- [ ] TNS / Oracle wallet files required?  Yes / No
  - Location: ________________________________________

---

### 6. Shared Dependencies

- [ ] Uses shared libraries with other BF components?  Yes / No
  - If Yes, list: ________________________________________
- [ ] Reads / writes to shared file system paths?  Yes / No
  - If Yes, list: ________________________________________
- [ ] Uses Windows Registry?  Yes / No  
  *(Blocker for Linux containers — must externalise)*
- [ ] Uses COM / DCOM?  Yes / No  
  *(Blocker for Linux containers)*
- [ ] Depends on Windows-specific system services?  Yes / No  
  *(May require Windows container)*

---

### 7. Classification Outcome

| Criterion | Result |
|-----------|--------|
| Technology | Java / C# / C++ / Mixed |
| Deployment model | Tomcat / IIS / Service wrapper / Standalone |
| Windows-only blockers? | None / Registry / COM / Windows service |
| Linux container feasible? | Yes / No (Windows container required) |
| Version freeze required? | Yes — preserve exact versions from current env |
| Dockerfile template to use | `dockerfiles/___/Dockerfile.___` |
| Priority | 1–5 |

---

### 8. Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| ADMS on-site confirmation | | | |
| Developer lead | | | |
| Security review | | | |

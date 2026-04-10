# High-Side / Low-Side Boundary Strategy

## 1. Scope Summary

| Domain | Count (approx) | Containerisation scope | Action |
|--------|---------------|----------------------|--------|
| Low-side active | ≤ 9 (confirm) | **IN SCOPE** | Containerise |
| Low-side retired | ≥ 1 confirmed | Out of scope | No action |
| High-side | ~43 | **OUT OF SCOPE** | No modification — document interfaces only |
| **Total system** | **~53** | | |

> **Golden rule:** High-side components are not touched, not modified, not redeployed.
> Any change to a low-side container that alters the interface with a high-side component
> is a **breaking change** and must be escalated before proceeding.

---

## 2. The Boundary

```
┌─────────────────────────────────────────────────────────────────────┐
│                        HIGH SIDE (out of scope)                     │
│                                                                     │
│   [Oracle DB] [High-side Brokers] [Legacy JS UI] [Other Services]  │
│        │              │                  │              │           │
│        └──────────────┴──────────────────┴──────────────┘          │
│                               │                                     │
│                    ┌──────────▼──────────┐                         │
│                    │   SECURITY BOUNDARY  │  ◄── Fixed interface    │
│                    │  (Gateway / Diode)   │      contracts          │
│                    └──────────┬──────────┘                         │
└───────────────────────────────┼─────────────────────────────────────┘
                                │
┌───────────────────────────────┼─────────────────────────────────────┐
│                        LOW SIDE (in scope)                          │
│                                                                     │
│   [Low Marshaller]   [Broker A]   [Broker B]   [Broker C] ...      │
│        ▲                 ▲             ▲             ▲              │
│        │                 │             │             │              │
│   (containerised)  (containerised) (containerised) (containerised)  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Interface Contract Preservation

### 3.1 What must not change

When containerising a low-side component, the following must be
**byte-for-byte / wire-format identical** to the existing deployment:

| Interface aspect | Constraint |
|-----------------|-----------|
| Network protocol | TCP/HTTP/JMS/proprietary — must match |
| Port number | Must match or be configurable to match |
| Message format | XML schema / binary format / encoding — must match |
| Character encoding | Must match (legacy systems often use non-UTF-8) |
| TLS/SSL version | Must negotiate same version as high-side expects |
| Certificate subjects / trust stores | Must be identical |
| Response timing characteristics | Timeouts must be compatible |
| Authentication mechanism | No changes to auth flows |

### 3.2 Interface contract documentation template

For each low-side component, capture:

```yaml
component: BF-LOW-001
interface_upstream:
  - component: BF-HIGH-0xx
    side: high
    protocol: HTTP
    port: 8080
    path: /api/v1/data
    method: POST
    content_type: application/xml
    encoding: UTF-8
    tls_version: TLSv1.2
    auth: mutual-TLS | basic | none
    message_schema: schemas/upstream-message.xsd
    max_message_size_kb: 512

interface_downstream:
  - component: BF-HIGH-0yy
    side: high
    # (same fields as above)
```

### 3.3 Testing the boundary

Before any low-side container is promoted to non-prod/prod:

1. **Wire-capture baseline**: Use Wireshark/tcpdump on the existing bare-metal
   deployment to capture traffic at the boundary. Store as regression test artefact.
2. **Replay test**: In the container test environment, replay captured traffic
   and verify responses are identical.
3. **Timing test**: Verify no significant latency increase (containers introduce
   ~1–5 ms per hop — confirm this is acceptable).
4. **Failover test**: Verify high-side components handle container restart gracefully
   (connection refused → retry → reconnect).

---

## 4. Version Freeze Policy

> **Do not update application or dependency versions unless there is a specific,
> documented reason to do so.** The risk of a version change breaking the interface
> with a 10+ year-old high-side component outweighs any benefit.

### Allowed exceptions to version freeze

| Exception | Justification required |
|-----------|----------------------|
| Known CVE with CVSS ≥ 9.0 | Security assessment + regression test |
| Build tool only (not runtime) | Maven/Gradle upgrade if not in final image |
| Base OS layer only | e.g. OS security patches in container base |
| Explicit requirement from ADMS | Written authorisation |

### Version freeze implementation in Docker

```dockerfile
# Pin EVERY version explicitly — no :latest tags
FROM eclipse-temurin:8u392-b08-jre-focal
# Not: FROM eclipse-temurin:8-jre-focal

# Pin apt packages
RUN apt-get install -y --no-install-recommends \
    libssl1.1=1.1.1f-1ubuntu2.21 \
 && rm -rf /var/lib/apt/lists/*

# Pin Maven dependencies in pom.xml — no version ranges
# Bad:  <version>[1.0,)</version>
# Good: <version>1.4.2</version>
```

---

## 5. Clean Build Requirement

The agreed legacy clean build process **must be preserved** for low-side components.
Containerisation offers an opportunity to formalise and automate this:

### Container-based clean build

```
┌────────────────────────────────────────────────────┐
│              Clean Build Container                  │
│                                                     │
│  FROM scratch (or minimal base)                     │
│  + Only approved build tools (frozen versions)      │
│  + Source code (no pre-compiled artifacts)          │
│  + No internet access (--network=none)              │
│  + Frozen dependency cache (pre-verified)           │
│                                                     │
│  OUTPUT: signed artifact + build log                │
└────────────────────────────────────────────────────┘
```

See `strategy/CLEAN-BUILD-PROCESS.md` for the full procedure.

---

## 6. Environment Differences: ATOM Dev vs Target

| Aspect | ATOM Dev Environment | Target Non-Prod | Target Prod |
|--------|---------------------|-----------------|-------------|
| Host OS | Windows Server 2008 R2 | CONFIRM | CONFIRM |
| Dev workstation | Windows 7 VM | — | — |
| Java version | CONFIRM | CONFIRM | CONFIRM |
| .NET version | CONFIRM | CONFIRM | CONFIRM |
| Tomcat version | CONFIRM | CONFIRM | CONFIRM |
| IIS version | IIS 7.5 | CONFIRM | CONFIRM |
| Oracle client | CONFIRM | CONFIRM | CONFIRM |

> **Action:** Confirm all target environment versions with John Isted / ADMS on-site
> before writing any Dockerfile. The container must replicate the **target** environment,
> not the dev environment.

### Known discrepancies to investigate

- Some VMs in the target environment have been updated without matching ATOM upgrades
- The clean build environment may differ from both ATOM and target — clarify which
  version set is authoritative for the clean build

---

## 7. Kubernetes / Orchestration Considerations for the Boundary

If the containers are deployed to Kubernetes (or similar):

```yaml
# Network policy: low-side pods may not initiate connections to high-side namespace
# (high-side initiates TO low-side, or via gateway only)
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: low-side-boundary
  namespace: low-side
spec:
  podSelector: {}
  policyTypes:
    - Egress
  egress:
    # Allow only to gateway IP range — block direct high-side access
    - to:
        - ipBlock:
            cidr: GATEWAY_CIDR/32

---
# Low-side pods should be on isolated nodes
apiVersion: v1
kind: Node
metadata:
  labels:
    security-zone: low-side
---
# Deployment: constrain to low-side nodes
spec:
  template:
    spec:
      nodeSelector:
        security-zone: low-side
      tolerations:
        - key: security-zone
          operator: Equal
          value: low-side
```

# WiseDrive OBD2 SDK - Publishing Guide

## Repositories

| Environment | Repository | URL |
|-------------|------------|-----|
| Development | wisedrive-sdk-snapshots | https://wisedrive.jfrog.io/artifactory/wisedrive-sdk-snapshots |
| Production  | wisedrive-sdk-releases  | https://wisedrive.jfrog.io/artifactory/wisedrive-sdk-releases |

## Quick Start

### 1. Configure Credentials

Credentials are read from `local.properties` (project root):
```properties
sdk.dir=/path/to/your/android-sdk
jfrog.user=kalyan@wisedrive.in
jfrog.token=YOUR_JFROG_TOKEN
```

Alternatively, set environment variables:
```bash
export JFROG_USER=kalyan@wisedrive.in
export JFROG_TOKEN=YOUR_JFROG_TOKEN
```

### 2. Build the SDK

```bash
cd wisedrive-obd2-sdk-android
./gradlew :sdk:assembleRelease
```

### 3. Publish SDK

**To Production (Releases):**
```bash
./gradlew :sdk:publishReleasePublicationToJFrogReleasesRepository
```

**To Development (Snapshots):**
```bash
# First change version to include -SNAPSHOT in sdk/build.gradle.kts:
# val sdkVersionName = "2.0.0-SNAPSHOT"
./gradlew :sdk:publishReleasePublicationToJFrogSnapshotsRepository
```

**Publish to both repositories:**
```bash
./gradlew :sdk:publish
```

### 4. Verify Publication

```bash
# Check releases
curl -u kalyan@wisedrive.in:YOUR_TOKEN \
  https://wisedrive.jfrog.io/artifactory/wisedrive-sdk-releases/com/wisedrive/obd2-sdk/

# Check snapshots
curl -u kalyan@wisedrive.in:YOUR_TOKEN \
  https://wisedrive.jfrog.io/artifactory/wisedrive-sdk-snapshots/com/wisedrive/obd2-sdk/
```

Or check the JFrog UI:
- Releases: https://wisedrive.jfrog.io/ui/admin/repositories/local/wisedrive-sdk-releases
- Snapshots: https://wisedrive.jfrog.io/ui/admin/repositories/local/wisedrive-sdk-snapshots

---

## Client Integration Instructions

Share this with your SDK clients:

### Step 1: Add Repository

In `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        
        // WiseDrive Private Repository
        maven {
            url = uri("https://wisedrive.jfrog.io/artifactory/wisedrive-sdk-releases")
            credentials {
                username = "CLIENT_USERNAME"  // Provided by WiseDrive
                password = "CLIENT_TOKEN"     // Provided by WiseDrive
            }
        }
    }
}
```

### Step 2: Add Dependency

In `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.wisedrive:obd2-sdk:2.0.0")
}
```

### Step 3: Initialize SDK

```kotlin
import com.wisedrive.obd2.WiseDriveOBD2SDK
import com.wisedrive.obd2.models.SDKConfig

val config = SDKConfig(
    apiKey = "your-api-key",
    useMock = false
)

val sdk = WiseDriveOBD2SDK.initialize(context, config)
```

---

## Published Artifact Details

| Field | Value |
|-------|-------|
| Group ID | `com.wisedrive` |
| Artifact ID | `obd2-sdk` |
| Current Version | `2.0.0` |
| Packaging | AAR |
| Encryption | RSA-4096 + AES-256-GCM |

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 2.0.0 | 2026-04 | Military-grade encryption, dual key system, dual submission architecture |
| 1.0.0 | 2026-01 | Initial release |

---

## Troubleshooting

### "401 Unauthorized" when publishing
- Check credentials in `local.properties`
- Verify token hasn't expired at https://wisedrive.jfrog.io/ui/admin/artifactory/user_profile
- Ensure user has deploy/write permissions on the repository

### "Could not resolve dependency" for clients
- Verify repository URL is correct
- Check client credentials have read access
- Ensure the version exists in the repository

### AAPT2 error on ARM machines
- Android build tools require x86_64 architecture
- Use a CI/CD runner with x86_64 or build on a standard x86_64 machine

---

## Support

- Email: sdk@wisedrive.in
- Documentation: https://wisedrive.in/sdk/docs
